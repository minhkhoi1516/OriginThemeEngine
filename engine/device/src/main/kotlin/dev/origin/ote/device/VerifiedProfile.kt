package dev.origin.ote.device

/** Evidence pins from this project's uploaded APK/library/resource analysis. */
internal object VerifiedProfile {
 const val ID = "pd2453-fingerprint-v1"
 const val MODEL = "V2453A"
 const val SDK = 36
 const val DIR = "/data/bbkcore/bbkthemeres/novoland/103/进化/content"
 const val TARGET = "$DIR/animation.zip"
 const val DESCRIPTION = "/data/bbkcore/bbkthemeres/novoland/103/进化/description.xml"
 const val APK = "/system/app/FingerprintUI/FingerprintUI.apk"
 const val LIBRARY = "/system/app/FingerprintUI/lib/arm64/libvkrenderer.so"
 const val DESCRIPTION_HASH = "fdf8399b61126103bca44ef17e5ea633f6f56babf91e1f9b5c0596af51291430"
 const val APK_HASH = "172f48d73bda68d5b9a4324976471ea71a22220227ea317499a9b2f478cd4f31"
 const val LIBRARY_HASH = "b09c0bf2f741766dfdee669ba485da3eaa6692c72b525ac6abfb435e9d7edcdc"
 const val CONTEXT = "u:object_r:vivo_data_file:s0:c512,c768"
 const val ORIGINAL_HASH = "050502563a6b1ee1e0904b74918c81d343cf4e76ff7f66791bb87055ad6fbbc4"
 val knownHashes = setOf(ORIGINAL_HASH,
  "fb99525bfed119caa13c262d72b96c88a08d8e2644ff93621dc5866bc42af8b0",
  "c2c62522f1ff6ab77aa14a4dbeb65cc90e24d5dfea8df258588b26fd1618224b",
  "2b781328725b61d556cafcb1ed0be3952c2c2dd5c547c52bcd3122a4c797851f",
  "e7e1a8a12367a15140d1a4195a50c67c438603e1b65d0da138ce91776719d0fe",
  "addef1ef682be3bdd7ebb8522d27c1a9b280f3e3f64110bc685a8331a9c9408b")
}
