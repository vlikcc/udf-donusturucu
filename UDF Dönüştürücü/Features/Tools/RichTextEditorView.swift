import SwiftUI
import UIKit

// MARK: - UITextView köprüsü

final class RichTextEditorProxy {
    weak var textView: UITextView?
    var onContentChange: ((NSAttributedString) -> Void)?

    private let defaultFamily = "Times New Roman"
    private let defaultSize: CGFloat = 12

    private var defaultFont: UIFont {
        UIFont(name: defaultFamily, size: defaultSize) ?? .systemFont(ofSize: defaultSize)
    }

    var hasSelection: Bool {
        guard let textView else { return false }
        return textView.selectedRange.length > 0
    }

    func toggleBold() { toggleTrait(.traitBold) }
    func toggleItalic() { toggleTrait(.traitItalic) }
    func toggleUnderline() { toggleUnderlineStyle() }

    func setAlignment(_ alignment: NSTextAlignment) {
        guard let textView else { return }
        let storage = textView.textStorage
        guard storage.length > 0 else { return }
        let nsString = storage.string as NSString
        let paraRange = nsString.paragraphRange(for: textView.selectedRange)

        storage.beginEditing()
        storage.enumerateAttribute(.paragraphStyle, in: paraRange) { value, range, _ in
            let style = ((value as? NSParagraphStyle)?.mutableCopy() as? NSMutableParagraphStyle)
                ?? NSMutableParagraphStyle()
            style.alignment = alignment
            storage.addAttribute(.paragraphStyle, value: style, range: range)
        }
        if storage.attribute(.paragraphStyle, at: paraRange.location, effectiveRange: nil) == nil {
            let style = NSMutableParagraphStyle()
            style.alignment = alignment
            storage.addAttribute(.paragraphStyle, value: style, range: paraRange)
        }
        storage.endEditing()
        notifyChange()
    }

    private func notifyChange() {
        guard let textView else { return }
        onContentChange?(textView.attributedText)
    }

    private func toggleTrait(_ trait: UIFontDescriptor.SymbolicTraits) {
        guard let textView else { return }
        let range = textView.selectedRange
        let storage = textView.textStorage

        if range.length == 0 {
            var typing = textView.typingAttributes
            let font = (typing[.font] as? UIFont) ?? defaultFont
            var traits = font.fontDescriptor.symbolicTraits
            if traits.contains(trait) { traits.remove(trait) } else { traits.insert(trait) }
            if let descriptor = font.fontDescriptor.withSymbolicTraits(traits) {
                typing[.font] = UIFont(descriptor: descriptor, size: font.pointSize)
                textView.typingAttributes = typing
            }
            notifyChange()
            return
        }

        storage.beginEditing()
        storage.enumerateAttribute(.font, in: range) { value, subRange, _ in
            let font = (value as? UIFont) ?? defaultFont
            var traits = font.fontDescriptor.symbolicTraits
            if traits.contains(trait) { traits.remove(trait) } else { traits.insert(trait) }
            if let descriptor = font.fontDescriptor.withSymbolicTraits(traits) {
                storage.addAttribute(.font, value: UIFont(descriptor: descriptor, size: font.pointSize), range: subRange)
            }
        }
        storage.endEditing()
        notifyChange()
    }

    private func toggleUnderlineStyle() {
        guard let textView else { return }
        let range = textView.selectedRange
        let storage = textView.textStorage

        if range.length == 0 {
            var typing = textView.typingAttributes
            let current = typing[.underlineStyle] as? Int ?? 0
            if current == 0 {
                typing[.underlineStyle] = NSUnderlineStyle.single.rawValue
            } else {
                typing.removeValue(forKey: .underlineStyle)
            }
            textView.typingAttributes = typing
            notifyChange()
            return
        }

        storage.beginEditing()
        var anyUnderlined = false
        storage.enumerateAttribute(.underlineStyle, in: range) { value, _, stop in
            if (value as? Int ?? 0) != 0 {
                anyUnderlined = true
                stop.pointee = true
            }
        }
        if anyUnderlined {
            storage.removeAttribute(.underlineStyle, range: range)
        } else {
            storage.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: range)
        }
        storage.endEditing()
        notifyChange()
    }

    func setTextColor(_ color: UIColor) {
        applyAttribute(.foregroundColor, value: color)
    }

    func setHighlightColor(_ color: UIColor?) {
        if let color {
            applyAttribute(.backgroundColor, value: color)
        } else if let textView {
            let range = textView.selectedRange
            guard range.length > 0 else { return }
            textView.textStorage.removeAttribute(.backgroundColor, range: range)
            notifyChange()
        }
    }

    func markField(name: String) {
        guard let textView else { return }
        let range = textView.selectedRange
        guard range.length > 0 else { return }
        textView.textStorage.addAttributes([
            .udfFieldName: name,
            .backgroundColor: UIColor.systemYellow.withAlphaComponent(0.35)
        ], range: range)
        notifyChange()
    }

    private func applyAttribute(_ key: NSAttributedString.Key, value: Any) {
        guard let textView else { return }
        let range = textView.selectedRange
        let storage = textView.textStorage

        if range.length == 0 {
            var typing = textView.typingAttributes
            typing[key] = value
            textView.typingAttributes = typing
            notifyChange()
            return
        }

        storage.addAttribute(key, value: value, range: range)
        notifyChange()
    }
}

struct RichTextEditorView: UIViewRepresentable {
    @Binding var attributedText: NSAttributedString
    var proxy: RichTextEditorProxy
    var isEditable: Bool = true
    var onChange: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> UITextView {
        let textView = UITextView()
        textView.delegate = context.coordinator
        textView.isEditable = isEditable
        textView.isScrollEnabled = true
        textView.backgroundColor = .clear
        textView.textContainerInset = UIEdgeInsets(top: 12, left: 8, bottom: 12, right: 8)
        textView.autocorrectionType = .yes
        textView.autocapitalizationType = .sentences
        textView.spellCheckingType = .yes
        textView.allowsEditingTextAttributes = true

        let font = UIFont(name: "Times New Roman", size: 12) ?? .systemFont(ofSize: 12)
        let style = NSMutableParagraphStyle()
        style.alignment = .justified
        textView.typingAttributes = [
            .font: font,
            .foregroundColor: UIColor.label,
            .paragraphStyle: style
        ]

        textView.attributedText = attributedText
        proxy.textView = textView
        return textView
    }

    func updateUIView(_ textView: UITextView, context: Context) {
        proxy.textView = textView
        proxy.onContentChange = { [self] updated in
            if !attributedText.isEqual(to: updated) {
                attributedText = updated
                onChange()
            }
        }
        textView.isEditable = isEditable

        if !textView.isFirstResponder, !textView.attributedText.isEqual(to: attributedText) {
            let selected = textView.selectedRange
            textView.attributedText = attributedText
            textView.selectedRange = selected
        }
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: RichTextEditorView

        init(parent: RichTextEditorView) {
            self.parent = parent
        }

        func textViewDidChange(_ textView: UITextView) {
            parent.attributedText = textView.attributedText
            parent.onChange()
        }
    }
}

// MARK: - Biçimlendirme araç çubuğu

struct RichTextFormattingToolbar: View {
    var proxy: RichTextEditorProxy
    var onInsertField: (() -> Void)?

    private let textColors: [(String, UIColor)] = [
        ("Siyah", .label),
        ("Kırmızı", .systemRed),
        ("Lacivert", UIColor(red: 0.11, green: 0.18, blue: 0.32, alpha: 1)),
        ("Yeşil", .systemGreen),
        ("Mavi", .systemBlue)
    ]

    private let highlightColors: [(String, UIColor?)] = [
        ("Yok", nil),
        ("Sarı", UIColor.systemYellow.withAlphaComponent(0.45)),
        ("Gri", UIColor.systemGray5),
        ("Turkuaz", UIColor.systemTeal.withAlphaComponent(0.35))
    ]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                formatButton("bold", systemName: "bold") { proxy.toggleBold() }
                formatButton("italic", systemName: "italic") { proxy.toggleItalic() }
                formatButton("underline", systemName: "underline") { proxy.toggleUnderline() }

                Divider().frame(height: 22)

                Menu {
                    ForEach(textColors, id: \.0) { item in
                        Button(item.0) { proxy.setTextColor(item.1) }
                    }
                } label: {
                    toolbarIcon("textformat")
                }

                Menu {
                    ForEach(highlightColors, id: \.0) { item in
                        Button(item.0) { proxy.setHighlightColor(item.1) }
                    }
                } label: {
                    toolbarIcon("highlighter")
                }

                if let onInsertField {
                    formatButton("field", systemName: "text.badge.plus") { onInsertField() }
                }

                Divider().frame(height: 22)

                formatButton("left", systemName: "text.alignleft") { proxy.setAlignment(.left) }
                formatButton("center", systemName: "text.aligncenter") { proxy.setAlignment(.center) }
                formatButton("justify", systemName: "text.justify") { proxy.setAlignment(.justified) }
                formatButton("right", systemName: "text.alignright") { proxy.setAlignment(.right) }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
        }
        .background(Color(.secondarySystemGroupedBackground))
    }

    private func toolbarIcon(_ systemName: String) -> some View {
        Image(systemName: systemName)
            .font(.body.weight(.semibold))
            .frame(width: 36, height: 36)
            .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 8))
    }

    private func formatButton(_ id: String, systemName: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.body.weight(.semibold))
                .frame(width: 36, height: 36)
                .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(id)
    }
}
