import Foundation

extension String {
    /// Longest prefix whose UTF-8 encoding fits `maxBytes`, cut on Unicode scalar
    /// boundaries. Matches Android `takeUtf8Bytes` and the firmware field sizes.
    public func prefixUTF8(maxBytes: Int) -> String {
        guard maxBytes > 0 else { return "" }
        var used = 0
        var scalars = String.UnicodeScalarView()
        for scalar in unicodeScalars {
            let size = String(scalar).utf8.count
            if used + size > maxBytes { break }
            scalars.append(scalar)
            used += size
        }
        return String(scalars)
    }

    /// First `count` UTF-16 code units, like Kotlin `String.take` on the JVM.
    func prefixUTF16(_ count: Int) -> String {
        let units = Array(utf16.prefix(count))
        return String(decoding: units, as: UTF16.self)
    }
}
