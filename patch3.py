import sys

content = open('app/src/main/java/com/example/ui/screens/FileCategoryActivity.kt').read()

new_compact = """@Composable
fun CompactFileRow(file: FileInfo, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateString = dateFormat.format(Date(file.dateModified))
    
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 24.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(GlassBorder),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = AuraCyan, modifier = Modifier.size(24.dp))
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file.contentUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatBytes(file.size), style = MaterialTheme.typography.bodySmall, color = AuraOrange)
                Text(" • $dateString", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.DeleteForever, contentDescription = "Secure Delete", tint = AuraRose)
        }
    }
}
"""

new_grid = """@Composable
fun GridFileItem(file: FileInfo, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(GlassBorder)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = AuraCyan, modifier = Modifier.size(32.dp))
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file.contentUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Overlays
        Box(
            modifier = Modifier.fillMaxSize().padding(4.dp)
        ) {
            // Delete button top right
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.align(Alignment.TopEnd).size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Secure Delete", tint = AuraRose, modifier = Modifier.size(16.dp))
            }
            
            // Size bottom left
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(formatBytes(file.size), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White)
            }
        }
    }
}
"""

# replace everything from @Composable fun CompactFileRow to EOF
parts = content.split('@Composable\nfun CompactFileRow')
if len(parts) > 1:
    new_content = parts[0] + new_compact + "\n" + new_grid
    open('app/src/main/java/com/example/ui/screens/FileCategoryActivity.kt', 'w').write(new_content)
    print("Patched successfully")
else:
    print("Could not find CompactFileRow")
