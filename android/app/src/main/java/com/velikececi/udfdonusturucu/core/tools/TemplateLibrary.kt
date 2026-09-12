package com.velikececi.udfdonusturucu.core.tools

/** TemplateField.swift'in Kotlin karşılığı. */
data class TemplateField(
    val key: String,
    val label: String,
    val placeholder: String,
    val multiline: Boolean = false,
)

/** PetitionTemplate.swift'in Kotlin karşılığı. [body] `{{alanAdi}}` yer tutucuları içerir. */
data class PetitionTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val fields: List<TemplateField>,
    val body: String,
)

/**
 * TemplateLibrary.swift'in Kotlin karşılığı — 6 dilekçe şablonu, gövde metinleri iOS'tan
 * birebir taşındı.
 *
 * NOT: Bu metinler genel iskelet niteliğindedir ve hukuki danışmanlık değildir. Yayın öncesi
 * bir hukukçu tarafından gözden geçirilmelidir.
 */
object TemplateLibrary {

    private val commonFooterFields = listOf(
        TemplateField("adSoyad", "Ad Soyad", "Adınız Soyadınız"),
        TemplateField("tcNo", "T.C. Kimlik No", "11111111111"),
        TemplateField("adres", "Adres", "Mahalle, Cadde, No, İlçe/İl"),
        TemplateField("tarih", "Tarih", "01.01.2026"),
    )

    val all: List<PetitionTemplate> = listOf(
        PetitionTemplate(
            id = "genel-dilekce",
            title = "Genel Dilekçe",
            subtitle = "Herhangi bir kuruma verilebilecek genel amaçlı dilekçe",
            fields = listOf(
                TemplateField("makam", "Hitap Edilen Makam", "ANKARA VALİLİĞİNE"),
                TemplateField("konu", "Konu", "Talebinizin kısa özeti"),
                TemplateField("aciklama", "Açıklamalar", "Talebinizi ayrıntılı yazın", multiline = true),
            ) + commonFooterFields,
            body = """
                {{makam}}

                KONU: {{konu}}

                {{aciklama}}

                Gereğinin yapılmasını saygılarımla arz ederim. {{tarih}}

                {{adSoyad}}
                T.C. Kimlik No: {{tcNo}}
                Adres: {{adres}}
            """.trimIndent(),
        ),
        PetitionTemplate(
            id = "itiraz-dilekcesi",
            title = "İtiraz Dilekçesi",
            subtitle = "Bir karara veya işleme itiraz için",
            fields = listOf(
                TemplateField("makam", "Hitap Edilen Makam", "... MAHKEMESİNE"),
                TemplateField("dosyaNo", "Dosya / Karar No", "2026/123"),
                TemplateField("itirazKonusu", "İtiraz Edilen Karar/İşlem", "İtiraz ettiğiniz karar veya işlem"),
                TemplateField("aciklama", "İtiraz Nedenleri", "İtiraz gerekçelerinizi yazın", multiline = true),
            ) + commonFooterFields,
            body = """
                {{makam}}

                DOSYA NO: {{dosyaNo}}

                KONU: {{itirazKonusu}} hakkında itirazlarımın sunulmasıdır.

                AÇIKLAMALAR:

                {{aciklama}}

                SONUÇ VE İSTEM: Yukarıda açıklanan nedenlerle itirazımın kabulüne karar verilmesini saygılarımla arz ve talep ederim. {{tarih}}

                {{adSoyad}}
                T.C. Kimlik No: {{tcNo}}
                Adres: {{adres}}
            """.trimIndent(),
        ),
        PetitionTemplate(
            id = "icra-itiraz",
            title = "İcra Takibine İtiraz",
            subtitle = "Ödeme emrine itiraz için icra dairesine dilekçe",
            fields = listOf(
                TemplateField("icraDairesi", "İcra Dairesi", "ANKARA ... İCRA DAİRESİNE"),
                TemplateField("dosyaNo", "İcra Dosya No", "2026/456"),
                TemplateField("alacakli", "Alacaklı", "Alacaklının adı/unvanı"),
                TemplateField("aciklama", "İtiraz Nedenleri", "Borca, faize, imzaya vb. itiraz nedenleriniz", multiline = true),
            ) + commonFooterFields,
            body = """
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
            """.trimIndent(),
        ),
        PetitionTemplate(
            id = "tanik-listesi",
            title = "Tanık Listesi",
            subtitle = "Mahkemeye sunulacak tanık bildirimi",
            fields = listOf(
                TemplateField("makam", "Mahkeme", "... MAHKEMESİNE"),
                TemplateField("dosyaNo", "Dosya No", "2026/789"),
                TemplateField(
                    "taniklar",
                    "Tanıklar (her satıra bir tanık: Ad Soyad, T.C., adres)",
                    "Ad Soyad, T.C. No, Adres",
                    multiline = true,
                ),
                TemplateField("konu", "Tanıkların Dinleneceği Konu", "Hangi vakıa için tanık bildiriyorsunuz"),
            ) + commonFooterFields,
            body = """
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
            """.trimIndent(),
        ),
        PetitionTemplate(
            id = "mazeret-dilekcesi",
            title = "Mazeret Dilekçesi",
            subtitle = "Duruşmaya katılamama mazereti bildirimi",
            fields = listOf(
                TemplateField("makam", "Mahkeme", "... MAHKEMESİNE"),
                TemplateField("dosyaNo", "Dosya No", "2026/321"),
                TemplateField("durusmaTarihi", "Duruşma Tarihi", "15.02.2026"),
                TemplateField("mazeret", "Mazeret", "Katılamama nedeninizi yazın (belge ekleyebilirsiniz)", multiline = true),
            ) + commonFooterFields,
            body = """
                {{makam}}

                DOSYA NO: {{dosyaNo}}

                KONU: {{durusmaTarihi}} tarihli duruşma için mazeretimin bildirilmesidir.

                AÇIKLAMALAR:

                {{mazeret}}

                Bu nedenle {{durusmaTarihi}} tarihli duruşmaya katılamayacağımdan, mazeretimin kabulü ile duruşmanın başka bir güne ertelenmesini saygılarımla arz ve talep ederim. {{tarih}}

                {{adSoyad}}
                T.C. Kimlik No: {{tcNo}}
                Adres: {{adres}}
            """.trimIndent(),
        ),
        PetitionTemplate(
            id = "ek-sure-talebi",
            title = "Ek Süre Talebi",
            subtitle = "Beyan/delil sunumu için ek süre istemi",
            fields = listOf(
                TemplateField("makam", "Mahkeme / Kurum", "... MAHKEMESİNE"),
                TemplateField("dosyaNo", "Dosya No", "2026/654"),
                TemplateField("islem", "Süre İstenen İşlem", "Örn. delillerin sunulması"),
                TemplateField("gerekce", "Gerekçe", "Ek süre talebinizin gerekçesi", multiline = true),
            ) + commonFooterFields,
            body = """
                {{makam}}

                DOSYA NO: {{dosyaNo}}

                KONU: {{islem}} için ek süre talebimizin sunulmasıdır.

                AÇIKLAMALAR:

                {{gerekce}}

                Bu nedenle {{islem}} için tarafıma uygun bir ek süre verilmesini saygılarımla arz ve talep ederim. {{tarih}}

                {{adSoyad}}
                T.C. Kimlik No: {{tcNo}}
                Adres: {{adres}}
            """.trimIndent(),
        ),
    )
}
