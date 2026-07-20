import SwiftUI
import UniformTypeIdentifiers

struct EncryptView: View {
    @State private var selectedFile: URL?
    @State private var password = ""
    @State private var passwordConfirm = ""
    @State private var showPicker = false
    @State private var isWorking = false
    @State private var resultURL: URL?
    @State private var errorMessage: String?
    @State private var shareURL: URL?

    private var passwordsValid: Bool {
        password.count >= 4 && password == passwordConfirm
    }

    var body: some View {
        List {
            Section {
                Button {
                    showPicker = true
                } label: {
                    Label(selectedFile?.lastPathComponent ?? "PDF Seç", systemImage: "doc.richtext.fill")
                        .lineLimit(1)
                }
            } footer: {
                Text("Şifrelenen PDF, yalnızca belirlediğiniz parola girilerek açılabilir. Parolayı unutursanız dosya kurtarılamaz.")
            }

            if selectedFile != nil {
                Section("Parola") {
                    SecureField("Parola (en az 4 karakter)", text: $password)
                    SecureField("Parola (tekrar)", text: $passwordConfirm)

                    if !password.isEmpty && !passwordsValid {
                        Text(password.count < 4 ? "Parola en az 4 karakter olmalı." : "Parolalar eşleşmiyor.")
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                }

                Section {
                    Button {
                        encrypt()
                    } label: {
                        if isWorking {
                            HStack {
                                ProgressView()
                                Text("Şifreleniyor...")
                            }
                        } else {
                            Label("Şifrele", systemImage: "lock.doc.fill")
                                .bold()
                        }
                    }
                    .disabled(!passwordsValid || isWorking)
                }
            }

            if let resultURL {
                Section("Sonuç") {
                    Label(resultURL.lastPathComponent, systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                        .font(.subheadline)

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
        .navigationTitle("PDF Şifreleme")
        .sheet(isPresented: $showPicker) {
            ToolDocumentPicker(types: [.pdf]) { urls in
                if let first = urls.first {
                    selectedFile = first
                    resultURL = nil
                    errorMessage = nil
                }
                showPicker = false
            }
        }
        .sheet(item: $shareURL) { url in
            ActivityViewController(activityItems: [url])
        }
    }

    private func encrypt() {
        guard let file = selectedFile else { return }
        isWorking = true
        errorMessage = nil
        resultURL = nil
        let filePassword = password

        Task {
            do {
                let output = try PDFToolsService.encrypt(url: file, password: filePassword)
                await MainActor.run {
                    resultURL = output
                    isWorking = false
                    ConversionStorage.shared.addRecord(
                        ConversionRecord(
                            originalFileName: output.lastPathComponent,
                            outputFormat: "PDF",
                            success: true,
                            outputPath: output.path
                        )
                    )
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isWorking = false
                }
            }
        }
    }
}
