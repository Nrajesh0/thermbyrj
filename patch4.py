import sys

content = open('app/src/main/java/com/example/ui/screens/FileCategoryActivity.kt').read()

import_statement = """import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
"""

content = content.replace("import coil.compose.AsyncImage\nimport coil.request.ImageRequest\n", import_statement)

async_image_compact = """AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file.contentUri)
                    .crossfade(true)
                    .build(),
                imageLoader = ImageLoader.Builder(LocalContext.current)
                    .components { add(VideoFrameDecoder.Factory()) }
                    .build(),
                contentDescription = null,"""

content = content.replace("""AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file.contentUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,""", async_image_compact)


open('app/src/main/java/com/example/ui/screens/FileCategoryActivity.kt', 'w').write(content)
print("Patched ImageLoader successfully")
