import Foundation
import Security

struct SignatureCertificate: Identifiable {
    let id = UUID()
    let subjectSummary: String
}

struct SignatureInspectionResult {
    let signatureEntryNames: [String]
    let certificates: [SignatureCertificate]
    let signingDates: [Date]

    var hasSignature: Bool { !signatureEntryNames.isEmpty }
}

enum SignatureInspectionError: LocalizedError {
    case cannotRead
    case notUDF

    var errorDescription: String? {
        switch self {
        case .cannotRead:
            return "Dosya okunamadı."
        case .notUDF:
            return "Geçersiz UDF dosyası."
        }
    }
}

/// İmzalı UDF dosyalarındaki CMS/PKCS#7 imza bloklarından sertifika ve imza zamanı bilgisini çıkarır.
/// Yalnızca bilgi amaçlıdır — sertifika zinciri veya imza bütünlüğü DOĞRULANMAZ.
final class SignatureInspector {

    static func inspect(udfURL: URL) throws -> SignatureInspectionResult {
        guard let data = try? Data(contentsOf: udfURL) else {
            throw SignatureInspectionError.cannotRead
        }
        guard let entries = try? ZIPExtractor.extractEntries(from: data) else {
            throw SignatureInspectionError.notUDF
        }

        let signatureKeywords = ["imza", "sign", ".p7s", ".sig", ".pkcs"]
        var signatureEntries = entries.filter { entry in
            let lower = entry.fileName.lowercased()
            return signatureKeywords.contains { lower.contains($0) }
        }

        // Ad eşleşmezse content/properties dışındaki girdilerde sertifika deseni ara.
        if signatureEntries.isEmpty {
            let known = ["content.xml", "documentproperties.xml", "properties.xml"]
            signatureEntries = entries.filter { entry in
                !known.contains(entry.fileName.lowercased()) && containsCertificatePattern(entry.data)
            }
        }

        var certificates: [SignatureCertificate] = []
        var seenSubjects = Set<String>()
        var signingDates: [Date] = []

        for entry in signatureEntries {
            for certData in extractCertificateCandidates(from: entry.data) {
                guard let cert = SecCertificateCreateWithData(nil, certData as CFData),
                      let summary = SecCertificateCopySubjectSummary(cert) as String? else { continue }
                if seenSubjects.insert(summary).inserted {
                    certificates.append(SignatureCertificate(subjectSummary: summary))
                }
            }
            signingDates.append(contentsOf: extractSigningTimes(from: entry.data))
        }

        return SignatureInspectionResult(
            signatureEntryNames: signatureEntries.map(\.fileName),
            certificates: certificates,
            signingDates: signingDates.sorted()
        )
    }

    // MARK: - DER yardımcıları

    /// 30 82 xx xx (uzun SEQUENCE) düğümlerini tarar; her adayı X.509 sertifikası olarak
    /// SecCertificateCreateWithData ile deneriz — geçerli olmayanlar elenir.
    private static func extractCertificateCandidates(from data: Data) -> [Data] {
        var candidates: [Data] = []
        let bytes = [UInt8](data)
        var index = 0

        while index < bytes.count - 4 {
            // SEQUENCE (0x30) + 2 baytlık uzunluk formu (0x82): tipik sertifika başlangıcı
            if bytes[index] == 0x30 && bytes[index + 1] == 0x82 {
                let length = (Int(bytes[index + 2]) << 8) | Int(bytes[index + 3])
                let totalLength = 4 + length
                if length > 200, index + totalLength <= bytes.count {
                    candidates.append(data.subdata(in: (data.startIndex + index)..<(data.startIndex + index + totalLength)))
                }
            }
            index += 1
        }
        return candidates
    }

    private static func containsCertificatePattern(_ data: Data) -> Bool {
        !extractCertificateCandidates(from: data).isEmpty
    }

    /// signingTime attribute'unu (OID 1.2.840.113549.1.9.5) bayt deseniyle bulup
    /// ardından gelen UTCTime/GeneralizedTime değerini okur.
    private static func extractSigningTimes(from data: Data) -> [Date] {
        // OID DER kodlaması: 06 09 2A 86 48 86 F7 0D 01 09 05
        let oidPattern: [UInt8] = [0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x09, 0x05]
        let bytes = [UInt8](data)
        var dates: [Date] = []

        var index = 0
        while index <= bytes.count - oidPattern.count {
            if Array(bytes[index..<(index + oidPattern.count)]) == oidPattern {
                // OID'den sonra SET (0x31) içinde UTCTime (0x17) veya GeneralizedTime (0x18) beklenir.
                var cursor = index + oidPattern.count
                let searchLimit = min(cursor + 8, bytes.count - 2)
                while cursor < searchLimit {
                    let tag = bytes[cursor]
                    if tag == 0x17 || tag == 0x18 {
                        let length = Int(bytes[cursor + 1])
                        let valueStart = cursor + 2
                        if length > 0, length < 32, valueStart + length <= bytes.count,
                           let raw = String(bytes: bytes[valueStart..<(valueStart + length)], encoding: .ascii),
                           let date = parseASN1Time(raw, generalized: tag == 0x18) {
                            dates.append(date)
                        }
                        break
                    }
                    cursor += 1
                }
                index += oidPattern.count
            } else {
                index += 1
            }
        }
        return dates
    }

    private static func parseASN1Time(_ value: String, generalized: Bool) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = generalized ? "yyyyMMddHHmmss'Z'" : "yyMMddHHmmss'Z'"
        return formatter.date(from: value)
    }
}
