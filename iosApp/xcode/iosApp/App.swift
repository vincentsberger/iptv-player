// Nur der Einstieg: die ganze App (Oberfläche und Logik) ist Kotlin im Framework IptvApp.
import SwiftUI
import IptvApp

@main
struct IptvPlayerApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
