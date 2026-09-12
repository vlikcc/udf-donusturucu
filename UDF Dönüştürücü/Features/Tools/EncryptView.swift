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

    private var fileExtension: String {
        selectedFile?.pathExtension.lowercased() ?? ""
    }

    private var isUDFInput: Bool {
        fileExtension == "udf" || fileExtension == "udfenc"
    }

    private var isEncryptedInput: Bool {
        fileExtension == "udfenc"
    }

    private var passwordsValid: Bool {
        password.count >= 4 && password == passwordConfirm
    }

    var body: some View {
        List {
            Section {
                Button { showPicker = true } label: {
                    Label(
                        selectedFile?.lastPathComponent ?? "PDF veya UDF Seç",
                        systemImage: isUDFInput ? "doc.fill" : "doc.richtext.fill"
                    )
                    .lineLimit(1)
                }
            } footer: {
                Text(isUDFInput
                     ? "UDF şifreleme uygulamaya özel .udfenc dosyası oluşturur. Şifreli dosyayı UYAP'ta kullanmadan önce bu ekrandan çözmeniz gerekir."
                     : "Şifrelenen PDF yalnızca belirlediğiniz parola girilerek açılabilir. Parolayı unutursanız dosya kurtarılamaz.")
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
                    Button { process() } label: {
                        if isWorking {
                            HStack {
                                ProgressView()
                                Text(isEncryptedInput ? "UDF açılıyor..." : "Şifreleniyor...")
                            }
                        } else {
                            Label(
                                isEncryptedInput ? "UDF'nin Şifresini Çöz" : (isUDFInput ? "UDF'yi Şifrele" : "PDF'yi Şifrele"),
                                systemImage: isEncryptedInput ? "lock.open.fill" : "lock.doc.fill"
                            )
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
        .navigationTitle("PDF / UDF Şifreleme")
        .sheet(isPresented: $showPicker) {
            ToolDocumentPicker(types: [.pdf] + UTType.udfPickerTypes) { urls in
                if let url = urls.first {
                    let ext = url.pathExtension.lowercased()
                    if ["pdf", "udf", "udfenc"].contains(ext) {
                        selectedFile = url
                        resultURL = nil
                        errorMessage = nil
                        password = ""
                        passwordConfirm = ""
                    } else {
                        errorMessage = "Lütfen bir PDF, UDF veya UDFENC dosyası seçin."
                    }
                }
                showPicker = false
            }
        }
        .sheet(item: $shareURL) { url in
            ActivityViewController(activityItems: [url])
        }
    }

    private func process() {
        guard let file = selectedFile else { return }
        isWorking = true
        errorMessage = nil
        resultURL = nil
        let filePassword = password

        Task {
            do {
                let output: URL
                if isEncryptedInput {
                    output = try UDFToolsService.decrypt(url: file, password: filePassword)
                } else if isUDFInput {
                    output = try UDFToolsService.encrypt(url: file, password: filePassword)
                } else {
                    output = try PDFToolsService.encrypt(url: file, password: filePassword)
                }

                await MainActor.run {
                    resultURL = output
                    isWorking = false
                    ConversionStorage.shared.addRecord(
                        ConversionRecord(
                            originalFileName: output.lastPathComponent,
                            outputFormat: output.pathExtension.uppercased(),
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
