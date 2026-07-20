import SwiftUI
import UniformTypeIdentifiers

struct SignatureInfoView: View {
    @State private var selectedFile: URL?
    @State private var showPicker = false
    @State private var result: SignatureInspectionResult?
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                Button {
                    showPicker = true
                } label: {
                    Label(selectedFile?.lastPathComponent ?? "UDF Dosyası Seç", systemImage: "signature")
                        .lineLimit(1)
                }
            } footer: {
                Text("Bilgi amaçlıdır; imzanın hukuki geçerliliği veya sertifika zinciri doğrulanmaz.")
            }

            if let result {
                if result.hasSignature {
                    if !result.certificates.isEmpty {
                        Section("Sertifikalar") {
                            ForEach(result.certificates) { cert in
                                HStack(spacing: 10) {
                                    Image(systemName: "person.crop.circle.badge.checkmark")
                                        .foregroundStyle(.green)
                                    Text(cert.subjectSummary)
                                        .font(.subheadline)
                                }
                            }
                        }
                    }

                    if !result.signingDates.isEmpty {
                        Section("İmza Zamanı") {
                            ForEach(result.signingDates, id: \.self) { date in
                                HStack(spacing: 10) {
                                    Image(systemName: "clock.badge.checkmark")
                                        .foregroundStyle(.orange)
                                    Text(date, format: .dateTime.day().month(.wide).year().hour().minute())
                                        .font(.subheadline)
                                }
                            }
                        }
                    }

                    Section("İmza Dosyaları") {
                        ForEach(result.signatureEntryNames, id: \.self) { name in
                            Text(name)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    if result.certificates.isEmpty && result.signingDates.isEmpty {
                        Section {
                            Text("İmza verisi bulundu ancak sertifika bilgisi okunamadı. Dosya farklı bir imza biçimi kullanıyor olabilir.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                } else {
                    Section {
                        ContentUnavailableView(
                            "İmza Bulunamadı",
                            systemImage: "signature",
                            description: Text("Bu UDF dosyasında elektronik imza verisi tespit edilemedi.")
                        )
                    }
                }
            }

            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("E-İmza Bilgisi")
        .sheet(isPresented: $showPicker) {
            ToolDocumentPicker(types: [.udfType]) { urls in
                showPicker = false
                guard let first = urls.first else { return }
                selectedFile = first
                inspect(url: first)
            }
        }
    }

    private func inspect(url: URL) {
        errorMessage = nil
        result = nil
        do {
            result = try SignatureInspector.inspect(udfURL: url)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
