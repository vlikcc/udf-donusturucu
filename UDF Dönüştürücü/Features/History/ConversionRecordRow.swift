import SwiftUI

/// Geçmiş ve dava dosyası ekranlarının paylaştığı belge satırı.
struct ConversionRecordRow: View {
    let record: ConversionRecord
    var onPreview: (URL) -> Void
    var onShare: (URL) -> Void
    var onSave: (ConversionRecord) -> Void
    /// Dava dosyası ekranında rozet gereksiz — belge zaten o dosyanın içinde.
    var showsCaseBadge: Bool = true

    @ObservedObject private var caseStore = CaseFileStore.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Self.formatIcon(record.outputFormat)
                    .frame(width: 40, height: 40)
                    .background(
                        Self.formatColor(record.outputFormat).opacity(0.1),
                        in: RoundedRectangle(cornerRadius: 10)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(record.originalFileName)
                        .font(.subheadline).bold()
                        .lineLimit(1)

                    HStack(spacing: 8) {
                        Text(record.outputFormat)
                            .font(.caption2).bold()
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Self.formatColor(record.outputFormat).opacity(0.15), in: Capsule())
                            .foregroundStyle(Self.formatColor(record.outputFormat))

                        Text(record.date, format: .dateTime.month(.abbreviated).day().hour().minute())
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    if showsCaseBadge, let file = caseStore.caseFile(id: record.caseFileID) {
                        HStack(spacing: 4) {
                            Image(systemName: "folder.fill")
                                .font(.system(size: 9))
                            Text(file.displayLabel)
                                .font(.caption2)
                                .lineLimit(1)
                        }
                        .foregroundStyle(file.color)
                        .padding(.top, 1)
                    }
                }

                Spacer()
            }

            HStack(spacing: 12) {
                DocumentActionButton(title: "Görüntüle", systemImage: "eye") {
                    if let url = record.resolvedURL { onPreview(url) }
                }

                DocumentActionButton(title: "Paylaş", systemImage: "square.and.arrow.up") {
                    if let url = record.resolvedURL { onShare(url) }
                }

                DocumentActionButton(title: "Kaydet", systemImage: "folder.badge.plus", tint: .green) {
                    onSave(record)
                }
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: - Biçim görselleri

    static func formatIcon(_ format: String) -> some View {
        let icon: String
        switch format.uppercased() {
        case "PDF": icon = "doc.richtext.fill"
        case "DOCX": icon = "doc.text.fill"
        case "UDF": icon = "doc.fill"
        default: icon = "doc.fill"
        }
        return Image(systemName: icon)
            .font(.title3)
            .foregroundStyle(formatColor(format))
    }

    static func formatColor(_ format: String) -> Color {
        switch format.uppercased() {
        case "PDF": return .red
        case "DOCX": return .blue
        case "UDF": return AppTheme.navy
        default: return .gray
        }
    }
}
