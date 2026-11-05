// app/build.gradle.kts

android {
    // ... aapka existing code ...
    
    // MuPDF native libraries ko support karne ke liye
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    // Purani PDFBox dependency ko hata dein:
    // implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    
    // MuPDF core dependency (Fitz)
    implementation("com.artifex.mupdf:fitz:1.23.0")
    
    // ... aapki baaki Compose aur Room dependencies ...
}
