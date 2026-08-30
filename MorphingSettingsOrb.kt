@Composable
fun MorphingSettingsOrb() {
    var isExpanded by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation_anim"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse_anim"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        
        // The Orb (Top Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 24.dp)
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { isExpanded = !isExpanded },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(32.dp).graphicsLayer {
                scaleX = pulse
                scaleY = pulse
                rotationZ = rotation
            }) {
                drawArc(
                    color = AuraCyan.copy(alpha = 0.6f),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = AuraRose.copy(alpha = 0.8f),
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Full Screen Overlay
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.05f, transformOrigin = TransformOrigin(0.9f, 0.1f), animationSpec = tween(400, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.05f, transformOrigin = TransformOrigin(0.9f, 0.1f), animationSpec = tween(300, easing = FastOutSlowInEasing)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Obsidian.copy(alpha = 0.95f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isExpanded = false } // Click background to close
            ) {
                // Settings Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume clicks so they don't close the overlay */ }
                ) {
                    Spacer(modifier = Modifier.height(64.dp))
                    
                    Text(
                        "SYSTEM PREFERENCES", 
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
                        color = AuraCyan
                    )
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    SettingsToggleRow(title = "Haptic Feedback", initial = true)
                    SettingsToggleRow(title = "Background Monitoring", initial = true)
                    SettingsToggleRow(title = "Extreme Heat Alerts", initial = true)
                    SettingsToggleRow(title = "Auto-Calibration", initial = false)
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(100.dp))
                            .clickable { isExpanded = false }
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 32.dp, vertical = 12.dp)
                    ) {
                        Text("CLOSE", color = TextPrimary, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp))
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(title: String, initial: Boolean) {
    var checked by remember { mutableStateOf(initial) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { checked = !checked }
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = if (checked) TextPrimary else TextSecondary, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Obsidian,
                checkedTrackColor = AuraCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = GlassPanel,
                uncheckedBorderColor = TextSecondary.copy(alpha = 0.3f)
            )
        )
    }
}
