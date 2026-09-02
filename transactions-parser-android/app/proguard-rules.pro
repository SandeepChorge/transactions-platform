# R8 configuration for the release build type (issue #9, Phase 7).
#
# The bar for a rule here: it must name a failure R8 can cause that no build-time check
# catches. Rules a library already ships as consumer rules are deliberately absent —
# copying them in hides which library owns the constraint and leaves a stale duplicate
# behind when that library changes its mind. Already covered upstream, do not re-add:
#
#   * PdfBox-Android keeps its reflectively-instantiated SecurityHandler subclasses and
#     the documentinterchange package (see proguard.txt inside pdfbox-android-2.0.27.0.aar).
#   * kotlinx-serialization keeps Companion, INSTANCE and serializer() on every
#     @Serializable class, and the descriptor field on every generated $$serializer.
#   * navigation-common keeps Navigator subclasses and NavArgs.fromBundle.
#   * Room keeps its generated DAO and database implementations.
#
# PdfBox's fonts, CMaps and glyph lists live in assets/, which resource shrinking does
# not touch — only res/ is shrunk. They need no rule.


# --- Readable release stack traces -------------------------------------------------------

# Without these, a crash from a published build arrives with neither a source file nor a
# line number, and mapping.txt cannot restore what was never emitted in the first place.
# Class and method names are still obfuscated; only the file and line survive.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


# --- Type-safe navigation routes ---------------------------------------------------------

# The nine @Serializable route classes. Navigation Compose builds each route pattern from
# the serializer's descriptor, and matches a destination against it at runtime — so a route
# that R8 gets wrong does not fail the build, it fails as a blank screen or a crash the
# first time someone navigates there.
#
# The serialName in that descriptor is a compile-time string constant, so obfuscation alone
# would not break the match. This keeps the classes anyway: they are nine tiny objects and
# data classes, the shrinking they cost is not measurable, and the failure they prevent is
# only reachable by hand on a device.
-keep,includedescriptorclasses class com.madtitan94.transactionsparser.**.navigation.*Route { *; }
-keep,includedescriptorclasses class com.madtitan94.transactionsparser.**.navigation.*Route$* { *; }


# --- Backup file format ------------------------------------------------------------------

# The nine @Serializable models in core:domain that define the on-disk JSON backup, whose
# property names *are* the JSON keys. Those keys are compile-time constants in the generated
# descriptor, so R8 does not rename them — but this is the one format in the app whose
# failure mode is silent: a backup that writes or restores wrongly loses the user's data
# rather than crashing, and requirement 7 is that this never happens. The models are small
# and the format is read back by future versions of the app, so it is worth making the file
# independent of anything R8 decides.
# Keyed on the annotation, not on the package: BackupCodec, BackupValidator and the
# other logic classes share this package and are ordinary code that should shrink.
-if @kotlinx.serialization.Serializable class com.madtitan94.transactionsparser.core.domain.backup.**
-keep,includedescriptorclasses class com.madtitan94.transactionsparser.core.domain.backup.<1> { *; }


# --- PdfBox's optional JPEG 2000 decoder --------------------------------------------------

# PdfBox's JPXFilter calls into com.gemalto.jp2.JP2Decoder, which ships in the separate
# jp2-android artifact this project does not depend on. R8 treats the dangling reference as
# a build error, so it has to be named here rather than ignored.
#
# Suppressing it costs nothing: JPXFilter decodes JPEG 2000 *images*, and core:pdf only ever
# runs PDFTextStripper. A statement containing such an image extracts its text exactly as it
# does today — this was already true before R8, and the class was already absent at runtime.
# If image extraction is ever added, add the dependency instead of widening this rule.
-dontwarn com.gemalto.jp2.JP2Decoder
