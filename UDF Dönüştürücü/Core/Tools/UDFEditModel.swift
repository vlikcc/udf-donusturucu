import Foundation
import UIKit

// MARK: - Düzenlenebilir UDF belge modeli

struct UDFEditDocument: Equatable {
    var headers: [UDFEditHeaderFooter] = []
    var blocks: [UDFEditBlock]
    var footers: [UDFEditHeaderFooter] = []
}

struct UDFEditHeaderFooter: Equatable, Identifiable {
    let id = UUID()
    var type: String = "default"
    var paragraphs: [UDFEditParagraph] = [UDFEditParagraph(runs: [UDFEditRun(text: "")])]
}

enum UDFEditBlock: Equatable, Identifiable {
    case paragraph(UDFEditParagraph)
    case table(UDFEditTable)

    var id: String {
        switch self {
        case .paragraph(let p): return "p-\(p.id.uuidString)"
        case .table(let t): return "t-\(t.id.uuidString)"
        }
    }
}

struct UDFEditParagraph: Equatable, Identifiable {
    let id = UUID()
    var alignment: Int = 3
    var spaceAbove: CGFloat = 1
    var spaceBelow: CGFloat = 1
    var leftIndent: CGFloat = 0
    var rightIndent: CGFloat = 0
    var firstLineIndent: CGFloat = 0
    var hangingIndent: CGFloat = 0
    var lineSpacing: CGFloat = 0
    var tabStops: [CGFloat] = []
    var runs: [UDFEditRun] = []
}

struct UDFEditRun: Equatable {
    enum Kind: Equatable {
        case content
        case field(name: String)
        case space
    }

    var kind: Kind = .content
    var text: String
    var isBold: Bool = false
    var isItalic: Bool = false
    var isUnderline: Bool = false
    var fontSize: CGFloat = 12
    var fontFamily: String = "Times New Roman"
    /// Java/UYAP signed ARGB (siyah = -16777216)
    var foregroundARGB: Int?
    var backgroundARGB: Int?

    var isField: Bool {
        if case .field = kind { return true }
        return false
    }

    var fieldName: String? {
        if case .field(let name) = kind { return name }
        return nil
    }
}

struct UDFEditTable: Equatable, Identifiable {
    let id = UUID()
    var columnCount: Int = 2
    var columnSpans: [Int] = []
    var border: String = "borderCell"
    var rows: [UDFEditTableRow] = []
}

struct UDFEditTableRow: Equatable, Identifiable {
    let id = UUID()
    var rowType: String = "dataRow"
    var cells: [UDFEditTableCell] = []
}

struct UDFEditTableCell: Equatable, Identifiable {
    let id = UUID()
    var colspan: Int = 1
    var rowspan: Int = 1
    var fillColorARGB: Int?
    var paragraphs: [UDFEditParagraph] = [UDFEditParagraph(runs: [UDFEditRun(text: "")])]

    /// Birleştirilmiş hücrelerde ilk paragrafı döndürür.
    func primaryParagraphValue() -> UDFEditParagraph {
        if paragraphs.isEmpty {
            return UDFEditParagraph(runs: [UDFEditRun(text: "")])
        }
        return paragraphs[0]
    }

    mutating func setPrimaryParagraph(_ paragraph: UDFEditParagraph) {
        if paragraphs.isEmpty {
            paragraphs = [paragraph]
        } else {
            paragraphs[0] = paragraph
        }
    }
}

// MARK: - Renk yardımcıları

enum UDFColorCodec {
    static func uiColor(fromJavaARGB value: Int) -> UIColor {
        let u = UInt32(bitPattern: Int32(value))
        let a = CGFloat((u >> 24) & 0xFF) / 255
        let r = CGFloat((u >> 16) & 0xFF) / 255
        let g = CGFloat((u >> 8) & 0xFF) / 255
        let b = CGFloat(u & 0xFF) / 255
        return UIColor(red: r, green: g, blue: b, alpha: max(a, 0.01))
    }

    static func javaARGB(from color: UIColor) -> Int {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 1
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        return (Int(a * 255) << 24) | (Int(r * 255) << 16) | (Int(g * 255) << 8) | Int(b * 255)
    }

    static func parse(_ raw: String?) -> Int? {
        guard let raw, !raw.isEmpty else { return nil }
        if let intVal = Int(raw.trimmingCharacters(in: .whitespaces)) {
            return intVal
        }
        let parts = raw.split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
        guard parts.count >= 3 else { return nil }
        return (255 << 24) | (parts[0] << 16) | (parts[1] << 8) | parts[2]
    }

    static func format(_ argb: Int) -> String {
        String(argb)
    }
}

extension NSAttributedString.Key {
    static let udfFieldName = NSAttributedString.Key("udfFieldName")
}
