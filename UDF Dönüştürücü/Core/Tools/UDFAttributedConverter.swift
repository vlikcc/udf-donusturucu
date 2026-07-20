import Foundation
import UIKit

/// NSAttributedString ↔ UDFEditParagraph/UDFEditRun dönüşümü (renk, alan, biçimlendirme).
enum UDFAttributedConverter {

    private static let defaultFamily = "Times New Roman"
    private static let defaultSize: CGFloat = 12

    // MARK: - Paragraf ↔ AttributedString

    static func attributedString(from paragraph: UDFEditParagraph) -> NSAttributedString {
        let result = NSMutableAttributedString()
        let paraStyle = paragraphStyle(for: paragraph)

        for run in paragraph.runs {
            guard !run.text.isEmpty else { continue }
            result.append(NSAttributedString(string: run.text, attributes: attributes(for: run, paragraphStyle: paraStyle)))
        }

        if result.length == 0 {
            result.append(NSAttributedString(string: "", attributes: [
                .font: defaultFont(),
                .foregroundColor: UIColor.label,
                .paragraphStyle: paraStyle
            ]))
        }
        return result
    }

    static func paragraph(from attributed: NSAttributedString, base: UDFEditParagraph) -> UDFEditParagraph {
        var updated = base
        let string = attributed.string as NSString
        guard string.length > 0 else {
            updated.runs = [UDFEditRun(text: "")]
            return updated
        }

        var paraRange = NSRange(location: 0, length: string.length)
        if string.character(at: string.length - 1) == 10 {
            paraRange.length -= 1
        }

        updated.alignment = alignmentCode(from: attributed, range: paraRange)
        updated.runs = runs(from: attributed, range: paraRange)
        return updated
    }

    static func attributedString(from document: UDFEditDocument) -> NSAttributedString {
        let result = NSMutableAttributedString()
        for (index, block) in document.blocks.enumerated() {
            if index > 0 { result.append(NSAttributedString(string: "\n")) }
            switch block {
            case .paragraph(let para):
                result.append(attributedString(from: para))
            case .table(let table):
                result.append(tablePreview(from: table))
            }
        }
        return result
    }

    // MARK: - Run attributes

    private static func attributes(for run: UDFEditRun, paragraphStyle: NSParagraphStyle) -> [NSAttributedString.Key: Any] {
        var attrs: [NSAttributedString.Key: Any] = [
            .font: font(for: run),
            .paragraphStyle: paragraphStyle
        ]

        if let fg = run.foregroundARGB {
            attrs[.foregroundColor] = UDFColorCodec.uiColor(fromJavaARGB: fg)
        } else {
            attrs[.foregroundColor] = UIColor.label
        }

        if let bg = run.backgroundARGB {
            attrs[.backgroundColor] = UDFColorCodec.uiColor(fromJavaARGB: bg)
        }

        if run.isUnderline {
            attrs[.underlineStyle] = NSUnderlineStyle.single.rawValue
        }

        if case .field(let name) = run.kind {
            attrs[.udfFieldName] = name
            if attrs[.backgroundColor] == nil {
                attrs[.backgroundColor] = UIColor.systemYellow.withAlphaComponent(0.35)
            }
        }

        return attrs
    }

    private static func font(for run: UDFEditRun) -> UIFont {
        let family = run.fontFamily.isEmpty ? defaultFamily : run.fontFamily
        let size = run.fontSize > 0 ? run.fontSize : defaultSize
        var base = UIFont(name: family, size: size) ?? .systemFont(ofSize: size)
        var traits = base.fontDescriptor.symbolicTraits
        if run.isBold { traits.insert(.traitBold) }
        if run.isItalic { traits.insert(.traitItalic) }
        if let descriptor = base.fontDescriptor.withSymbolicTraits(traits) {
            base = UIFont(descriptor: descriptor, size: size)
        }
        return base
    }

    private static func paragraphStyle(for paragraph: UDFEditParagraph) -> NSParagraphStyle {
        let style = NSMutableParagraphStyle()
        switch paragraph.alignment {
        case 1: style.alignment = .center
        case 2: style.alignment = .right
        case 3: style.alignment = .justified
        default: style.alignment = .left
        }
        style.paragraphSpacingBefore = paragraph.spaceAbove
        style.paragraphSpacing = paragraph.spaceBelow
        style.headIndent = paragraph.leftIndent
        style.firstLineHeadIndent = paragraph.firstLineIndent
        if !paragraph.tabStops.isEmpty {
            style.tabStops = paragraph.tabStops.map { NSTextTab(textAlignment: .left, location: $0) }
        }
        return style
    }

    private static func alignmentCode(from attributed: NSAttributedString, range: NSRange) -> Int {
        guard range.length > 0,
              let style = attributed.attribute(.paragraphStyle, at: range.location, effectiveRange: nil) as? NSParagraphStyle else {
            return 3
        }
        switch style.alignment {
        case .center: return 1
        case .right: return 2
        case .justified: return 3
        default: return 0
        }
    }

    private static func runs(from attributed: NSAttributedString, range: NSRange) -> [UDFEditRun] {
        guard range.length > 0 else { return [UDFEditRun(text: "")] }

        var result: [UDFEditRun] = []
        attributed.enumerateAttributes(in: range, options: []) { attrs, subRange, _ in
            let text = (attributed.string as NSString).substring(with: subRange)
            guard !text.isEmpty else { return }

            let font = (attrs[.font] as? UIFont) ?? defaultFont()
            let traits = font.fontDescriptor.symbolicTraits
            let underline = (attrs[.underlineStyle] as? Int ?? 0) != 0
            let fieldName = attrs[.udfFieldName] as? String

            var kind: UDFEditRun.Kind = .content
            if text == " " || text == "\u{00A0}" {
                kind = .space
            } else if let fieldName, !fieldName.isEmpty {
                kind = .field(name: fieldName)
            }

            var foreground: Int?
            if let color = attrs[.foregroundColor] as? UIColor {
                foreground = UDFColorCodec.javaARGB(from: color)
            }

            var background: Int?
            if let color = attrs[.backgroundColor] as? UIColor {
                background = UDFColorCodec.javaARGB(from: color)
            }

            result.append(UDFEditRun(
                kind: kind,
                text: text,
                isBold: traits.contains(.traitBold),
                isItalic: traits.contains(.traitItalic),
                isUnderline: underline,
                fontSize: font.pointSize,
                fontFamily: resolvedFamily(from: font),
                foregroundARGB: foreground,
                backgroundARGB: background
            ))
        }
        return result.isEmpty ? [UDFEditRun(text: "")] : result
    }

    private static func tablePreview(from table: UDFEditTable) -> NSAttributedString {
        var lines: [String] = []
        for row in table.rows {
            let cells = row.cells.map { cell in
                cell.paragraphs.map { $0.runs.map(\.text).joined() }.joined(separator: " ")
            }
            lines.append(cells.joined(separator: " | "))
        }
        let text = lines.joined(separator: "\n")
        let style = NSMutableParagraphStyle()
        style.alignment = .left
        return NSAttributedString(string: text, attributes: [
            .font: UIFont.monospacedSystemFont(ofSize: 11, weight: .regular),
            .foregroundColor: UIColor.secondaryLabel,
            .paragraphStyle: style
        ])
    }

    private static func defaultFont() -> UIFont {
        UIFont(name: defaultFamily, size: defaultSize) ?? .systemFont(ofSize: defaultSize)
    }

    private static func resolvedFamily(from font: UIFont) -> String {
        let name = font.fontName.lowercased()
        if name.contains("times") { return defaultFamily }
        if name.contains("helvetica") { return "Helvetica" }
        if name.contains("arial") { return "Arial" }
        if name.contains("courier") { return "Courier New" }
        let family = font.familyName
        return family.isEmpty ? defaultFamily : family
    }
}
