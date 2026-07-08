import SwiftUI

/// Geçmiş/Sonuç ekranlarındaki "Görüntüle / Paylaş / Kaydet" gibi satır aksiyonları için
/// ikon üstte, kısa metin altta kompakt buton. Yatay Label(icon+text) düzeni üç buton yan
/// yana sığdırılınca metnin ortadan bölünmesine yol açıyordu; bu düzen tek satırda kalır.
struct DocumentActionButton: View {
    let title: String
    let systemImage: String
    var tint: Color = AppTheme.navy
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: systemImage)
                    .font(.system(size: 16, weight: .medium))
                Text(title)
                    .font(.caption2)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
        .buttonStyle(.bordered)
        .tint(tint)
    }
}
