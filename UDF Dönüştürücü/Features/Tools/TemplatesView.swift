import SwiftUI

struct TemplatesView: View {
    var body: some View {
        List {
            Section {
                ForEach(TemplateLibrary.all) { template in
                    NavigationLink {
                        TemplateFormView(template: template)
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(template.title)
                                .font(.subheadline).bold()
                            Text(template.subtitle)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 2)
                    }
                }
            } footer: {
                Text("Şablonlar genel bilgilendirme amaçlıdır ve hukuki danışmanlık yerine geçmez.")
            }
        }
        .navigationTitle("Dilekçe Şablonları")
    }
}

struct TemplateFormView: View {
    let template: PetitionTemplate

    @State private var values: [String: String] = [:]
    @State private var resultURL: URL?
    @State private var errorMessage: String?
    @State private var shareURL: URL?
    @State private var previewURL: URL?
    @State private var showTextPreview = false

    var body: some View {
        List {
            Section("Bilgiler") {
                ForEach(template.fields) { field in
                    if field.multiline {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(field.label)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            TextEditor(text: binding(for: field.key))
                                .frame(minHeight: 90)
                                .font(.subheadline)
                        }
                        .padding(.vertical, 2)
                    } else {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(field.label)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            TextField(field.placeholder, text: binding(for: field.key))
                                .font(.subheadline)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }

            Section {
                Button {
                    showTextPreview = true
                } label: {
                    Label("Metni Önizle", systemImage: "eye")
                }

                Button {
                    generate(format: "UDF")
                } label: {
                    Label("UDF Oluştur", systemImage: "doc.fill")
                        .bold()
                }

                Button {
                    generate(format: "PDF")
                } label: {
                    Label("PDF Oluştur", systemImage: "doc.richtext.fill")
                }
            }

            if let resultURL {
                Section("Sonuç") {
                    Label(resultURL.lastPathComponent, systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                        .font(.subheadline)

                    Button { previewURL = resultURL } label: {
                        Label("Görüntüle", systemImage: "eye")
                    }
                    Button { shareURL = resultURL } label: {
                        Label("Paylaş", systemImage: "square.and.arrow.up")
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
        .navigationTitle(template.title)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showTextPreview) {
            NavigationStack {
                ScrollView {
                    Text(TemplateEngine.fill(template, values: values))
                        .font(.system(size: 14, design: .serif))
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                }
                .navigationTitle("Önizleme")
                .navigationBarTitleDisplayMode(.inline)
            }
        }
        .sheet(item: $shareURL) { url in
            ActivityViewController(activityItems: [url])
        }
        .navigationDestination(item: $previewURL) { url in
            DocumentPreviewView(url: url)
        }
    }

    private func binding(for key: String) -> Binding<String> {
        Binding(
            get: { values[key] ?? "" },
            set: { values[key] = $0 }
        )
    }

    private func generate(format: String) {
        errorMessage = nil
        resultURL = nil
        do {
            let output: URL
            if format == "UDF" {
                output = try TemplateEngine.createUDF(template: template, values: values)
            } else {
                output = try TemplateEngine.createPDF(template: template, values: values)
            }
            resultURL = output
            ConversionStorage.shared.addRecord(
                ConversionRecord(
                    originalFileName: output.lastPathComponent,
                    outputFormat: format,
                    success: true,
                    outputPath: output.path
                )
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
