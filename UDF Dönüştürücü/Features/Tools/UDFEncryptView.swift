import SwiftUI
import UniformTypeIdentifiers

struct UDFEncryptView: View {
    @State private var selectedFile: URL?
    @State private var password = ""
    @State private var passwordConfirm = ""
    @State private var showPicker = false
    @State private var isWorking = false
    @State private var resultURL: URL?
    @State private var errorMessage: String?
    @State private var shareURL: URL?

    private var isEncryptedInput: Bool {
        selectedFile?.pathExtension.lowercased() == "udfenc"
    }

    private var passwordsValid: Bool {
        password.count >= 4 && password == passwordConfirm
    }

    var body: some View {
        List {
            Section {
                Button { showPicker = true } label: {
                    Label(selectedFile?.lastPathComponent ?? "UDF Seç", systemImage: "doc.fill")
                        .lineLimit(1)
                }
            } footer: {
                Text("UDF şifreleme uygulamaya özel .udfenc dosyası oluşturur. Şifreli dosyayı UYAP'ta kullanmadan önce bu ekrandan çözmeniz gerekir.")
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
                                Text(isEncryptedInput ? "UDF açılıyor..." : "UDF şifreleniyor...")
                            }
                        } else {
                            Label(
                                isEncryptedInput ? "UDF'nin Şifresini Çöz" : "UDF'yi Şifrele",
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
        .navigationTitle("UDF Şifreleme")
        .sheet(isPresented: $showPicker) {
            ToolDocumentPicker(types: UTType.udfPickerTypes, allowsMultipleSelection: false) { urls in
                if let url = urls.first(where: {
                    ["udf", "udfenc"].contains($0.pathExtension.lowercased())
                }) {
                    selectedFile = url
                    resultURL = nil
                    errorMessage = nil
                    password = ""
                    passwordConfirm = ""
                } else if !urls.isEmpty {
                    errorMessage = "Lütfen .udf veya .udfenc dosyası seçin."
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
                let output = isEncryptedInput
                    ? try UDFToolsService.decrypt(url: file, password: filePassword)
                    : try UDFToolsService.encrypt(url: file, password: filePassword)
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
