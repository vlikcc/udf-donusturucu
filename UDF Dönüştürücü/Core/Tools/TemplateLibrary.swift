import Foundation
import UIKit

struct TemplateField: Identifiable {
    let key: String
    let label: String
    let placeholder: String
    var multiline: Bool = false

    var id: String { key }
}

struct PetitionTemplate: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let fields: [TemplateField]
    /// {{alanAdi}} yer tutucuları içeren gövde metni.
    let body: String
}

/// Şablon metinlerini doldurup UDF/PDF üretimine hazır paragraf listesine çevirir.
final class TemplateEngine {

    static func fill(_ template: PetitionTemplate, values: [String: String]) -> String {
        var text = template.body
        for field in template.fields {
            let value = values[field.key]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            text = text.replacingOccurrences(of: "{{\(field.key)}}", with: value.isEmpty ? "................" : value)
        }
        return text
    }

    /// Satır bazlı paragraf üretir: tamamı BÜYÜK HARF olan kısa satırlar ortalanır ve kalın yazılır
    /// (mahkeme başlığı, "EK:" vb.), diğer satırlar iki yana yaslanır.
    static func makeParagraphs(from text: String) -> [UDFCreator.InputParagraph] {
        text.components(separatedBy: "\n").map { line in
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            let isHeading = !trimmed.isEmpty
                && trimmed.count < 80
                && trimmed == trimmed.uppercased(with: Locale(identifier: "tr_TR"))
                && trimmed.rangeOfCharacter(from: .uppercaseLetters) != nil

            return UDFCreator.InputParagraph(
                runs: [UDFCreator.InputRun(
                    text: line,
                    isBold: isHeading,
                    isItalic: false,
                    isUnderline: false,
                    fontSize: 12,
                    fontFamily: "Times New Roman"
                )],
                alignment: isHeading ? 1 : 3
            )
        }
    }

    static func createUDF(template: PetitionTemplate, values: [String: String]) throws -> URL {
        let text = fill(template, values: values)
        let paragraphs = makeParagraphs(from: text)
        return try UDFCreator.create(fileName: template.title, paragraphs: paragraphs)
    }

    static func createPDF(template: PetitionTemplate, values: [String: String]) throws -> URL {
        let text = fill(template, values: values)
        let document = UDFDocument(
            fileName: template.title,
            content: UDFContent(
                text: text,
                rawContent: text,
                contentType: .plainText,
                sections: [UDFSection(title: nil, body: text, level: 0)],
                tables: [],
                isRTF: false,
                formattedString: nil
            ),
            metadata: nil,
            pageFormat: nil
        )
        return try PDFConverter.convert(document: document)
    }
}

// MARK: - Şablon içerikleri
// NOT: Bu metinler genel iskelet niteliğindedir ve hukuki danışmanlık değildir.
// Yayın öncesi bir hukukçu tarafından gözden geçirilmelidir.

enum TemplateLibrary {

    private static let commonFooterFields: [TemplateField] = [
        TemplateField(key: "adSoyad", label: "Ad Soyad", placeholder: "Adınız Soyadınız"),
        TemplateField(key: "tcNo", label: "T.C. Kimlik No", placeholder: "11111111111"),
        TemplateField(key: "adres", label: "Adres", placeholder: "Mahalle, Cadde, No, İlçe/İl"),
        TemplateField(key: "tarih", label: "Tarih", placeholder: "01.01.2026")
    ]

    static let all: [PetitionTemplate] = [
        PetitionTemplate(
            id: "genel-dilekce",
            title: "Genel Dilekçe",
            subtitle: "Herhangi bir kuruma verilebilecek genel amaçlı dilekçe",
            fields: [
                TemplateField(key: "makam", label: "Hitap Edilen Makam", placeholder: "ANKARA VALİLİĞİNE"),
                TemplateField(key: "konu", label: "Konu", placeholder: "Talebinizin kısa özeti"),
                TemplateField(key: "aciklama", label: "Açıklamalar", placeholder: "Talebinizi ayrıntılı yazın", multiline: true)
            ] + commonFooterFields,
            body: """
            {{makam}}

            KONU: {{konu}}

            {{aciklama}}

            Gereğinin yapılmasını saygılarımla arz ederim. {{tarih}}

            {{adSoyad}}
            T.C. Kimlik No: {{tcNo}}
            Adres: {{adres}}
            """
        ),
        PetitionTemplate(
            id: "itiraz-dilekcesi",
            title: "İtiraz Dilekçesi",
            subtitle: "Bir karara veya işleme itiraz için",
            fields: [
                TemplateField(key: "makam", label: "Hitap Edilen Makam", placeholder: "... MAHKEMESİNE"),
                TemplateField(key: "dosyaNo", label: "Dosya / Karar No", placeholder: "2026/123"),
                TemplateField(key: "itirazKonusu", label: "İtiraz Edilen Karar/İşlem", placeholder: "İtiraz ettiğiniz karar veya işlem"),
                TemplateField(key: "aciklama", label: "İtiraz Nedenleri", placeholder: "İtiraz gerekçelerinizi yazın", multiline: true)
            ] + commonFooterFields,
            body: """
            {{makam}}

            DOSYA NO: {{dosyaNo}}

            KONU: {{itirazKonusu}} hakkında itirazlarımın sunulmasıdır.

            AÇIKLAMALAR:

            {{aciklama}}

            SONUÇ VE İSTEM: Yukarıda açıklanan nedenlerle itirazımın kabulüne karar verilmesini saygılarımla arz ve talep ederim. {{tarih}}

            {{adSoyad}}
            T.C. Kimlik No: {{tcNo}}
            Adres: {{adres}}
            """
        ),
        PetitionTemplate(
            id: "icra-itiraz",
            title: "İcra Takibine İtiraz",
            subtitle: "Ödeme emrine itiraz için icra dairesine dilekçe",
            fields: [
                TemplateField(key: "icraDairesi", label: "İcra Dairesi", placeholder: "ANKARA ... İCRA DAİRESİNE"),
                TemplateField(key: "dosyaNo", label: "İcra Dosya No", placeholder: "2026/456"),
                TemplateField(key: "alacakli", label: "Alacaklı", placeholder: "Alacaklının adı/unvanı"),
                TemplateField(key: "aciklama", label: "İtiraz Nedenleri", placeholder: "Borca, faize, imzaya vb. itiraz nedenleriniz", multiline: true)
            ] + commonFooterFields,
            body: """
            {{icraDairesi}}

            DOSYA NO: {{dosyaNo}}

            İTİRAZ EDEN (BORÇLU): {{adSoyad}} — T.C. {{tcNo}}
            ALACAKLI: {{alacakli}}

            KONU: Ödeme emrine itirazlarımın sunulmasıdır.

            AÇIKLAMALAR:

            {{aciklama}}

            Bu nedenlerle borca ve tüm fer'ilerine itiraz ediyorum. Takibin durdurulmasına karar verilmesini saygılarımla arz ve talep ederim. {{tarih}}

            {{adSoyad}}
            Adres: {{adres}}
            """
        ),
        PetitionTemplate(
            id: "tanik-listesi",
            title: "Tanık Listesi",
            subtitle: "Mahkemeye sunulacak tanık bildirimi",
            fields: [
                TemplateField(key: "makam", label: "Mahkeme", placeholder: "... MAHKEMESİNE"),
                TemplateField(key: "dosyaNo", label: "Dosya No", placeholder: "2026/789"),
                TemplateField(key: "taniklar", label: "Tanıklar (her satıra bir tanık: Ad Soyad, T.C., adres)", placeholder: "Ad Soyad, T.C. No, Adres", multiline: true),
                TemplateField(key: "konu", label: "Tanıkların Dinleneceği Konu", placeholder: "Hangi vakıa için tanık bildiriyorsunuz")
            ] + commonFooterFields,
            body: """
            {{makam}}

            DOSYA NO: {{dosyaNo}}

            KONU: Tanık listemizin sunulmasıdır.

            Aşağıda kimlik ve adres bilgileri yazılı tanıkların {{konu}} hakkında dinlenmesini talep ederim.

            TANIK LİSTESİ:

            {{taniklar}}

            Saygılarımla arz ederim. {{tarih}}

            {{adSoyad}}
            T.C. Kimlik No: {{tcNo}}
            Adres: {{adres}}
            """
        ),
        PetitionTemplate(
            id: "mazeret-dilekcesi",
            title: "Mazeret Dilekçesi",
            subtitle: "Duruşmaya katılamama mazereti bildirimi",
            fields: [
                TemplateField(key: "makam", label: "Mahkeme", placeholder: "... MAHKEMESİNE"),
                TemplateField(key: "dosyaNo", label: "Dosya No", placeholder: "2026/321"),
                TemplateField(key: "durusmaTarihi", label: "Duruşma Tarihi", placeholder: "15.02.2026"),
                TemplateField(key: "mazeret", label: "Mazeret", placeholder: "Katılamama nedeninizi yazın (belge ekleyebilirsiniz)", multiline: true)
            ] + commonFooterFields,
            body: """
            {{makam}}

            DOSYA NO: {{dosyaNo}}

            KONU: {{durusmaTarihi}} tarihli duruşma için mazeretimin bildirilmesidir.

            AÇIKLAMALAR:

            {{mazeret}}

            Bu nedenle {{durusmaTarihi}} tarihli duruşmaya katılamayacağımdan, mazeretimin kabulü ile duruşmanın başka bir güne ertelenmesini saygılarımla arz ve talep ederim. {{tarih}}

            {{adSoyad}}
            T.C. Kimlik No: {{tcNo}}
            Adres: {{adres}}
            """
        ),
        PetitionTemplate(
            id: "ek-sure-talebi",
            title: "Ek Süre Talebi",
            subtitle: "Beyan/delil sunumu için ek süre istemi",
            fields: [
                TemplateField(key: "makam", label: "Mahkeme / Kurum", placeholder: "... MAHKEMESİNE"),
                TemplateField(key: "dosyaNo", label: "Dosya No", placeholder: "2026/654"),
                TemplateField(key: "islem", label: "Süre İstenen İşlem", placeholder: "Örn. delillerin sunulması"),
                TemplateField(key: "gerekce", label: "Gerekçe", placeholder: "Ek süre talebinizin gerekçesi", multiline: true)
            ] + commonFooterFields,
            body: """
            {{makam}}

            DOSYA NO: {{dosyaNo}}

            KONU: {{islem}} için ek süre talebimizin sunulmasıdır.

            AÇIKLAMALAR:

            {{gerekce}}

            Bu nedenle {{islem}} için tarafıma uygun bir ek süre verilmesini saygılarımla arz ve talep ederim. {{tarih}}

            {{adSoyad}}
            T.C. Kimlik No: {{tcNo}}
            Adres: {{adres}}
            """
        )
    ]
}
