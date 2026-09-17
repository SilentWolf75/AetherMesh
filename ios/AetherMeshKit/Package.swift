// swift-tools-version: 5.9
// AetherMeshKit: the transport-independent core of the iOS app — wire format,
// chat/control crypto, and protocol policies. Builds and tests on Linux so it
// can be verified without a Mac; the SwiftUI + CoreBluetooth app depends on it.
import PackageDescription

let package = Package(
    name: "AetherMeshKit",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "AetherMeshKit", targets: ["AetherMeshKit"]),
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-protobuf.git", from: "1.28.0"),
        .package(url: "https://github.com/apple/swift-crypto.git", "3.0.0"..<"5.0.0"),
    ],
    targets: [
        .target(
            name: "AetherMeshKit",
            dependencies: [
                .product(name: "SwiftProtobuf", package: "swift-protobuf"),
                .product(name: "Crypto", package: "swift-crypto"),
                .product(name: "CryptoExtras", package: "swift-crypto"),
            ]
        ),
        .testTarget(
            name: "AetherMeshKitTests",
            dependencies: ["AetherMeshKit"]
        ),
    ]
)
