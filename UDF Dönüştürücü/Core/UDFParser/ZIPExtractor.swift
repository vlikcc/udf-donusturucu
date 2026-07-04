import Foundation
import Compression

final class ZIPExtractor {

    struct ZIPEntry {
        let fileName: String
        let data: Data
    }

    static func extractEntries(from data: Data) throws -> [ZIPEntry] {
        let bytes = [UInt8](data)
        let count = bytes.count

        // First, parse the Central Directory to get accurate sizes
        let centralEntries = try parseCentralDirectory(bytes: bytes, count: count)

        if centralEntries.isEmpty {
            throw UDFParserError.invalidZipArchive
        }

        var entries: [ZIPEntry] = []

        for cd in centralEntries {
            // Read the local file header at the offset specified by the central directory
            let localOffset = cd.localHeaderOffset
            guard localOffset + 30 <= count else { continue }

            // Verify local file header signature
            guard bytes[localOffset] == 0x50,
                  bytes[localOffset + 1] == 0x4B,
                  bytes[localOffset + 2] == 0x03,
                  bytes[localOffset + 3] == 0x04 else { continue }

            let localFileNameLength = readUInt16(bytes, localOffset + 26)
            let localExtraLength = readUInt16(bytes, localOffset + 28)
            let dataStart = localOffset + 30 + localFileNameLength + localExtraLength

            // Skip directories
            guard !cd.fileName.hasSuffix("/"), cd.compressedSize > 0 else { continue }
            guard dataStart + cd.compressedSize <= count else { continue }

            let compressedData = Data(bytes[dataStart..<dataStart + cd.compressedSize])

            let fileData: Data
            if cd.compressionMethod == 0 {
                fileData = compressedData
            } else if cd.compressionMethod == 8 {
                fileData = try inflate(compressedData, expectedSize: cd.uncompressedSize)
            } else {
                continue
            }

            entries.append(ZIPEntry(fileName: cd.fileName, data: fileData))
        }

        if entries.isEmpty {
            throw UDFParserError.invalidZipArchive
        }

        return entries
    }

    // MARK: - Central Directory Parsing

    private struct CentralDirectoryEntry {
        let fileName: String
        let compressionMethod: Int
        let compressedSize: Int
        let uncompressedSize: Int
        let localHeaderOffset: Int
    }

    private static func parseCentralDirectory(bytes: [UInt8], count: Int) throws -> [CentralDirectoryEntry] {
        // Find End of Central Directory record (search from end)
        // EOCD signature: 0x06054b50
        var eocdOffset = -1
        let minEOCDSize = 22
        let searchStart = max(0, count - 65557) // max comment size is 65535

        for i in stride(from: count - minEOCDSize, through: searchStart, by: -1) {
            if bytes[i] == 0x50 && bytes[i + 1] == 0x4B &&
               bytes[i + 2] == 0x05 && bytes[i + 3] == 0x06 {
                eocdOffset = i
                break
            }
        }

        guard eocdOffset >= 0 else {
            throw UDFParserError.invalidZipArchive
        }

        let cdOffset = readUInt32(bytes, eocdOffset + 16)
        let cdEntryCount = readUInt16(bytes, eocdOffset + 10)

        var entries: [CentralDirectoryEntry] = []
        var offset = cdOffset

        for _ in 0..<cdEntryCount {
            guard offset + 46 <= count else { break }

            // Central directory file header signature: 0x02014b50
            guard bytes[offset] == 0x50 && bytes[offset + 1] == 0x4B &&
                  bytes[offset + 2] == 0x01 && bytes[offset + 3] == 0x02 else { break }

            let compressionMethod = readUInt16(bytes, offset + 10)
            let compressedSize = readUInt32(bytes, offset + 20)
            let uncompressedSize = readUInt32(bytes, offset + 24)
            let fileNameLength = readUInt16(bytes, offset + 28)
            let extraLength = readUInt16(bytes, offset + 30)
            let commentLength = readUInt16(bytes, offset + 32)
            let localHeaderOffset = readUInt32(bytes, offset + 42)

            let fileNameStart = offset + 46
            guard fileNameStart + fileNameLength <= count else { break }

            let fileNameData = Data(bytes[fileNameStart..<fileNameStart + fileNameLength])
            let fileName = String(data: fileNameData, encoding: .utf8) ?? ""

            entries.append(CentralDirectoryEntry(
                fileName: fileName,
                compressionMethod: compressionMethod,
                compressedSize: compressedSize,
                uncompressedSize: uncompressedSize,
                localHeaderOffset: localHeaderOffset
            ))

            offset = fileNameStart + fileNameLength + extraLength + commentLength
        }

        return entries
    }

    // MARK: - Helper

    private static func readUInt16(_ bytes: [UInt8], _ offset: Int) -> Int {
        Int(UInt16(bytes[offset]) | (UInt16(bytes[offset + 1]) << 8))
    }

    private static func readUInt32(_ bytes: [UInt8], _ offset: Int) -> Int {
        Int(UInt32(bytes[offset])
            | (UInt32(bytes[offset + 1]) << 8)
            | (UInt32(bytes[offset + 2]) << 16)
            | (UInt32(bytes[offset + 3]) << 24))
    }

    // MARK: - Deflate

    private static func inflate(_ data: Data, expectedSize: Int) throws -> Data {
        let bufferSize = max(expectedSize * 2, 65536)
        var result = Data(count: bufferSize)
        let inputCount = data.count

        let decompressedSize = data.withUnsafeBytes { (inputPointer: UnsafeRawBufferPointer) -> Int in
            result.withUnsafeMutableBytes { (outputPointer: UnsafeMutableRawBufferPointer) -> Int in
                guard let inputBase = inputPointer.baseAddress,
                      let outputBase = outputPointer.baseAddress else { return 0 }
                return compression_decode_buffer(
                    outputBase.assumingMemoryBound(to: UInt8.self), bufferSize,
                    inputBase.assumingMemoryBound(to: UInt8.self), inputCount,
                    nil,
                    COMPRESSION_ZLIB
                )
            }
        }

        guard decompressedSize > 0 else {
            throw UDFParserError.parsingFailed("Deflate acma basarisiz.")
        }

        result.count = decompressedSize
        return result
    }
}
