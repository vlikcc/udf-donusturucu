import CryptoKit
import Foundation

struct UDFCompressionResult {
    let outputURL: URL
    let originalBytes: Int64
    let compressedBytes: Int64
}

enum UDFToolsError: LocalizedError {
    case cannotOpen(String)
    case invalidDocument(String)
    case noFiles
    case passwordRequired
    case invalidPassword
    case encryptionFailed
    case writeFailed

    var errorDescription: String? {
        switch self {
        case .cannotOpen(let name): return "\(name) dosyası açılamadı."
        case .invalidDocument(let name): return "\(name) geçerli bir UDF belgesi değil."
        case .noFiles: return "En az iki UDF dosyası seçmelisiniz."
        case .passwordRequired: return "Parola gerekli."
        case .invalidPassword: return "Parola hatalı veya şifreli dosya bozuk."
        case .encryptionFailed: return "UDF dosyası şifrelenemedi."
        case .writeFailed: return "UDF dosyası kaydedilemedi."
        }
    }
}

/// UDF ZIP arşivlerini UYAP formatını koruyarak işleyen araçlar.
///
/// `.udf` dosyaları UYAP tarafından ZIP arşivi olarak okunur. Bu nedenle parola eklemek,
/// standart ZIP'e parola koymak yerine uygulamaya özel `.udfenc` zarfı üretir. `.udfenc`
/// dosyası UYAP'a doğrudan gönderilmeden önce bu uygulamada parolası açılmalıdır.
final class UDFToolsService {

    static func merge(urls: [URL]) throws -> URL {
        let udfURLs = urls.filter { $0.pathExtension.lowercased() == "udf" }
        guard udfURLs.count >= 2, udfURLs.count == urls.count else {
            throw UDFToolsError.invalidDocument("Yalnızca UDF dosyaları birleştirilebilir.")
        }

        var mergedBlocks: [UDFEditBlock] = []
        var headers: [UDFEditHeaderFooter] = []
        var footers: [UDFEditHeaderFooter] = []

        for (index, url) in udfURLs.enumerated() {
            let parsed: UDFDocument
            do {
                parsed = try UDFParser.parse(fileURL: url)
            } catch {
                throw UDFToolsError.invalidDocument(url.lastPathComponent)
            }

            let model = UDFStructureParser.parse(
                rawXML: parsed.content.rawContent,
                plainText: parsed.content.text
            )
            if index == 0 {
                headers = model.headers
                footers = model.footers
            }
            mergedBlocks.append(contentsOf: model.blocks)
        }

        guard !mergedBlocks.isEmpty else {
            throw UDFToolsError.invalidDocument("Seçilen UDF dosyalarında içerik bulunamadı.")
        }

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmm"
        let fileName = "Birlestirilmis_UDF_\(formatter.string(from: Date()))"
        return try UDFCreator.create(
            fileName: fileName,
            document: UDFEditDocument(headers: headers, blocks: mergedBlocks, footers: footers)
        )
    }

    static func compress(url: URL) throws -> UDFCompressionResult {
        guard url.pathExtension.lowercased() == "udf" else {
            throw UDFToolsError.invalidDocument(url.lastPathComponent)
        }
        let originalData: Data
        let entries: [ZIPExtractor.ZIPEntry]
        do {
            originalData = try Data(contentsOf: url)
            entries = try ZIPExtractor.extractEntries(from: originalData)
        } catch {
            throw UDFToolsError.invalidDocument(url.lastPathComponent)
        }

        let compressedArchiveData = UDFCreator.buildZIPArchiveDeflate(
            entries: entries.map { ($0.fileName, $0.data) }
        )
        let archiveData = compressedArchiveData.count < originalData.count ? compressedArchiveData : originalData
        let outputURL = PDFConverter.outputDirectory()
            .appendingPathComponent("Sikistirilmis_\(url.deletingPathExtension().lastPathComponent)")
            .appendingPathExtension("udf")
        do {
            try archiveData.write(to: outputURL, options: .atomic)
        } catch {
            throw UDFToolsError.writeFailed
        }

        return UDFCompressionResult(
            outputURL: outputURL,
            originalBytes: Int64(originalData.count),
            compressedBytes: Int64(archiveData.count)
        )
    }

    static func encrypt(url: URL, password: String) throws -> URL {
        guard !password.isEmpty else { throw UDFToolsError.passwordRequired }
        guard url.pathExtension.lowercased() == "udf" else {
            throw UDFToolsError.invalidDocument(url.lastPathComponent)
        }

        let plainData: Data
        do {
            plainData = try Data(contentsOf: url)
            _ = try ZIPExtractor.extractEntries(from: plainData)
        } catch {
            throw UDFToolsError.invalidDocument(url.lastPathComponent)
        }

        let salt = randomData(count: 16)
        let key = deriveKey(password: password, salt: salt)
        let sealedBox: AES.GCM.SealedBox
        do {
            sealedBox = try AES.GCM.seal(plainData, using: key)
        } catch {
            throw UDFToolsError.encryptionFailed
        }

        // Format: magic (7) + salt (16) + nonce (12) + ciphertext + tag (16).
        var encrypted = Data("UDFENC1".utf8)
        encrypted.append(salt)
        encrypted.append(contentsOf: sealedBox.nonce)
        encrypted.append(sealedBox.ciphertext)
        encrypted.append(sealedBox.tag)

        let outputURL = PDFConverter.outputDirectory()
            .appendingPathComponent("Sifreli_\(url.deletingPathExtension().lastPathComponent)")
            .appendingPathExtension("udfenc")
        do {
            try encrypted.write(to: outputURL, options: .atomic)
            return outputURL
        } catch {
            throw UDFToolsError.writeFailed
        }
    }

    static func decrypt(url: URL, password: String) throws -> URL {
        guard !password.isEmpty else { throw UDFToolsError.passwordRequired }
        guard url.pathExtension.lowercased() == "udfenc" else {
            throw UDFToolsError.invalidDocument(url.lastPathComponent)
        }

        let encrypted: Data
        do {
            encrypted = try Data(contentsOf: url)
        } catch {
            throw UDFToolsError.invalidDocument(url.lastPathComponent)
        }
        let headerLength = 7 + 16 + 12 + 16
        guard encrypted.count > headerLength,
              encrypted.prefix(7) == Data("UDFENC1".utf8) else {
            throw UDFToolsError.invalidPassword
        }

        let salt = encrypted.subdata(in: 7..<23)
        let nonceData = encrypted.subdata(in: 23..<35)
        let combined = encrypted.subdata(in: 35..<encrypted.count)
        let key = deriveKey(password: password, salt: salt)

        let plainData: Data
        do {
            let nonce = try AES.GCM.Nonce(data: nonceData)
            guard combined.count >= 16 else { throw UDFToolsError.invalidPassword }
            let ciphertext = combined.dropLast(16)
            let tag = combined.suffix(16)
            let box = try AES.GCM.SealedBox(
                nonce: nonce,
                ciphertext: Data(ciphertext),
                tag: Data(tag)
            )
            plainData = try AES.GCM.open(box, using: key)
            _ = try ZIPExtractor.extractEntries(from: plainData)
        } catch {
            throw UDFToolsError.invalidPassword
        }

        let outputURL = PDFConverter.outputDirectory()
            .appendingPathComponent("Cozulmus_\(url.deletingPathExtension().lastPathComponent)")
            .appendingPathExtension("udf")
        do {
            try plainData.write(to: outputURL, options: .atomic)
            return outputURL
        } catch {
            throw UDFToolsError.writeFailed
        }
    }

    private static func deriveKey(password: String, salt: Data) -> SymmetricKey {
        // CryptoKit'in HKDF'i parolayı salt ile uygulamaya özel AES-256 anahtarına dönüştürür.
        HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: Data(password.utf8)),
            salt: salt,
            info: Data("UDFToolsService-UDFENC-v1".utf8),
            outputByteCount: 32
        )
    }

    private static func randomData(count: Int) -> Data {
        Data((0..<count).map { _ in UInt8.random(in: .min ... .max) })
    }
}
