package cn.nanoturtle.rootmys9280.manager.demo.expressive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Square
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SplitButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import kotlin.math.roundToInt

/**
 * The demo screen, one scrollable column of the things the design language actually changed.
 *
 * Sections are deliberately self-contained: each one is a small, copyable answer to "what does M3
 * Expressive look like here", and each one takes its motion from `MaterialTheme.motionScheme` so
 * the whole page can be switched to the standard scheme in one place.
 */
@Composable
fun ExpressiveDemoScreen(
    darkPreference: Boolean?,
    onDarkPreference: (Boolean?) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = "Expressive",
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                    )
                },
                subtitle = { Text("motion physics · 35 shapes · emphasized type") },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HeroCard() }
            item { ThemeCard(darkPreference, onDarkPreference) }
            item { ShapeMorphCard() }
            item { ButtonGroupCard() }
            item { ActionCard() }
            item { ToolbarCard() }
            item { LoadingCard() }
            item { MotionCard() }
            item { TypeCard() }
            item { ColourCard() }
        }
    }
}

/** The brand block: one 48 dp shape, one emphasized headline, no hardcoded pixels in between. */
@Composable
private fun HeroCard() {
    val colours = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colours.primaryContainer,
        contentColor = colours.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(96.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(Brush.linearGradient(listOf(colours.primary, colours.tertiary))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = colours.onPrimary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(
                    text = "Material 3\nExpressive",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Spring-driven motion, shape morph, and a colour system built from roles.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * A single-choice row built from `ToggleButton`s with the connected shape set: the checked button
 * morphs into a pill and its neighbours square off. This is the shape transition the design
 * language leans on most, and it is the component's own — no hand-animated corner radius anywhere.
 */
@Composable
private fun ThemeCard(darkPreference: Boolean?, onDarkPreference: (Boolean?) -> Unit) {
    val options =
        remember {
            listOf<Triple<String, Boolean?, ImageVector>>(
                Triple("Follow system", null, Icons.Rounded.BrightnessAuto),
                Triple("Light", false, Icons.Rounded.LightMode),
                Triple("Dark", true, Icons.Rounded.DarkMode),
            )
        }
    val selectedIndex = options.indexOfFirst { it.second == darkPreference }.coerceAtLeast(0)
    DemoCard(title = "Colour & contrast") {
        Text(
            text = "Fixed roles keep the same tone in light and dark; the rest swap ends of the tonal ramp.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        ConnectedToggleRow(
            labels = options.map { it.first },
            icons = options.map { it.third },
            selectedIndex = selectedIndex,
            onSelect = { index -> onDarkPreference(options[index].second) },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Resolved theme: ${options[selectedIndex].first.lowercase()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The shape library with a real morph between two of its members. */
@Composable
private fun ShapeMorphCard() {
    val colours = MaterialTheme.colorScheme
    val shapes =
        remember {
            listOf(
                MaterialShapes.Circle,
                MaterialShapes.Clover4Leaf,
                MaterialShapes.Cookie9Sided,
                MaterialShapes.Burst,
            )
        }
    val labels = remember { listOf("Circle", "Clover", "Cookie", "Burst") }
    val icons =
        remember {
            listOf(
                Icons.Rounded.Circle,
                Icons.Rounded.Favorite,
                Icons.Rounded.Star,
                Icons.Rounded.Bolt,
            )
        }
    var selected by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }
    val morph =
        remember(selected) {
            Morph(shapes[(selected + shapes.lastIndex) % shapes.size], shapes[selected])
        }
    val slowSpatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(selected) {
        progress.snapTo(0f)
        progress.animateTo(1f, slowSpatial)
    }
    val shape = remember(morph) { MorphOutlineShape(morph) { progress.value } }

    DemoCard(title = "Shape morph · ${labels[selected]}") {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(180.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(colours.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier.size(132.dp)
                        .clip(shape)
                        .background(Brush.linearGradient(listOf(colours.primary, colours.secondary)))
            )
        }
        Spacer(Modifier.height(16.dp))
        ConnectedToggleRow(
            labels = labels,
            icons = icons,
            selectedIndex = selected,
            onSelect = { selected = it },
        )
    }
}

/**
 * The button group: actions that make room for each other and spill into an overflow menu instead
 * of shrinking every target to nothing when the window is narrow.
 */
@Composable
private fun ButtonGroupCard() {
    var lastAction by remember { mutableStateOf("none") }
    val actions =
        remember {
            listOf(
                Triple("Search", Icons.Rounded.Search, "search"),
                Triple("Filter", Icons.Rounded.FilterList, "filter"),
                Triple("Share", Icons.Rounded.Share, "share"),
                Triple("Add", Icons.Rounded.Add, "add"),
            )
        }
    DemoCard(title = "Button group") {
        ButtonGroup(
            overflowIndicator = { state ->
                ButtonGroupDefaults.OverflowIndicator(menuState = state)
            },
            expandedRatio = 0.8f,
        ) {
            actions.forEach { (label, icon, action) ->
                customItem(
                    buttonGroupContent = {
                        FilledIconButton(onClick = { lastAction = action }) {
                            Icon(icon, contentDescription = label)
                        }
                    },
                    menuContent = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Last action: $lastAction",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Connected toggle buttons: leading/middle/trailing shapes, checked one becomes a pill. */
@Composable
private fun ConnectedToggleRow(
    labels: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { index, label ->
            ToggleButton(
                checked = index == selectedIndex,
                onCheckedChange = { checked -> if (checked) onSelect(index) },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.semantics { role = Role.RadioButton },
            ) {
                Icon(icons[index], contentDescription = label)
            }
        }
    }
}

/** Expressive buttons: a split button (two shapes that read as one) and the three fills. */
@Composable
private fun ActionCard() {
    var starred by remember { mutableStateOf(false) }
    DemoCard(title = "Actions") {
        SplitButton(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(onClick = {}) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Start root")
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = starred,
                    onCheckedChange = { starred = it },
                ) {
                    Icon(
                        if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Favourite",
                        modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                    )
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = {}) { Text("Filled") }
            FilledTonalButton(onClick = {}) { Text("Tonal") }
            OutlinedButton(onClick = {}) { Text("Outlined") }
        }
    }
}

/**
 * The floating toolbar: an expressive container that expands and collapses with the *fast* spatial
 * spring, with its own FAB as the leading action.
 */
@Composable
private fun ToolbarCard() {
    var expanded by remember { mutableStateOf(true) }
    DemoCard(title = "Floating toolbar") {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(180.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.BottomCenter,
        ) {
            HorizontalFloatingToolbar(
                expanded = expanded,
                floatingActionButton = {
                    FilledIconButton(onClick = { expanded = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Expand")
                    }
                },
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                TextButton(onClick = {}) { Text("Search") }
                TextButton(onClick = {}) { Text("Filter") }
                TextButton(onClick = {}) { Text("Share") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Expanded", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Switch(checked = expanded, onCheckedChange = { expanded = it })
        }
    }
}

/** Loading and progress, all of it shape-aware: the indicator morphs between polygons as it spins. */
@Composable
private fun LoadingCard() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.animateTo(1f, tween(durationMillis = 2400, easing = LinearEasing))
            progress.animateTo(0f, tween(durationMillis = 500))
        }
    }
    DemoCard(title = "Loading & progress") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Indeterminate", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ContainedLoadingIndicator(progress = { progress.value })
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(progress.value * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        LinearWavyProgressIndicator(progress = { progress.value })
    }
}

/**
 * The two motion channels side by side.
 *
 * Pressing the square is a spatial animation (scale), the caption change is an effects animation
 * (fade) — both from the same scheme, which is the point of having a scheme at all.
 */
@Composable
private fun MotionCard() {
    val colours = MaterialTheme.colorScheme
    val scheme = MaterialTheme.motionScheme
    val fastSpatial = scheme.fastSpatialSpec<Float>()
    val defaultSpatial = scheme.defaultSpatialSpec<IntOffset>()
    val slowSpatial = scheme.slowSpatialSpec<IntOffset>()
    val defaultEffects = scheme.defaultEffectsSpec<Float>()
    val fastEffects = scheme.fastEffectsSpec<Float>()

    var pressed by remember { mutableStateOf(false) }
    var second by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        scale.animateTo(if (pressed) 0.88f else 1f, fastSpatial)
    }

    DemoCard(title = "Motion physics") {
        Box(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier.size(104.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .clip(MaterialTheme.shapes.medium)
                        .background(colours.primary)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    pressed = true
                                    tryAwaitRelease()
                                    pressed = false
                                }
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "hold",
                    style = MaterialTheme.typography.labelLarge,
                    color = colours.onPrimary,
                )
            }
        }
        Text(
            text = "spatial spring 0.6 · 800   /   effects spring 1.0 · 1600",
            style = MaterialTheme.typography.bodySmall,
            color = colours.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = second,
            transitionSpec = {
                val spatial = if (targetState) defaultSpatial else slowSpatial
                val direction = if (targetState) 1 else -1
                (fadeIn(defaultEffects) + slideInVertically(spatial) { it / 4 * direction })
                    .togetherWith(fadeOut(fastEffects))
            },
            label = "motion-channels",
        ) { showSecond ->
            Text(
                text =
                    if (showSecond) "effects channel: colour and alpha, no bounds change"
                    else "spatial channel: position, size and shape",
                style = MaterialTheme.typography.bodyLargeEmphasized,
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { second = !second }) { Text("Switch channel") }
    }
}

/** The emphasized type scale — the one expressive addition a theme wrapper cannot fake. */
@Composable
private fun TypeCard() {
    DemoCard(title = "Emphasized type") {
        Text("Expressive", style = MaterialTheme.typography.displaySmallEmphasized)
        Spacer(Modifier.height(4.dp))
        Text("headlineEmphasized", style = MaterialTheme.typography.headlineSmallEmphasized)
        Spacer(Modifier.height(4.dp))
        Text("titleLargeEmphasized", style = MaterialTheme.typography.titleLargeEmphasized)
        Spacer(Modifier.height(4.dp))
        Text(
            "bodyLargeEmphasized — heavier stroke, tighter tracking; body copy keeps the body scale.",
            style = MaterialTheme.typography.bodyLargeEmphasized,
        )
    }
}

/** The role vocabulary, including the fixed roles the app's generator used to leave at baseline. */
@Composable
private fun ColourCard() {
    val colours = MaterialTheme.colorScheme
    val rows =
        listOf(
            listOf(
                "primary" to colours.primary,
                "secondary" to colours.secondary,
                "tertiary" to colours.tertiary,
            ),
            listOf(
                "primaryFixed" to colours.primaryFixed,
                "primaryFixedDim" to colours.primaryFixedDim,
                "tertiaryFixed" to colours.tertiaryFixed,
            ),
            listOf(
                "container" to colours.surfaceContainer,
                "containerHighest" to colours.surfaceContainerHighest,
                "outlineVariant" to colours.outlineVariant,
            ),
        )
    DemoCard(title = "Colour roles") {
        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            SwatchRow(row)
        }
    }
}

@Composable
private fun SwatchRow(entries: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { (label, colour) ->
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(44.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(colour)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.small,
                            )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Every section is the same surface so the page reads as one system rather than a pile of demos. */
@Composable
private fun DemoCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * A [Shape] that rasterises a [Morph] at a progress read from a lambda.
 *
 * `Morph` itself has no `Shape`, and material3's `RoundedPolygon.toShape()` only holds one static
 * polygon, so the morphing case is the one line of glue the library leaves to callers. The polygon
 * space is normalised to a unit square, which is why the path is scaled by the layout size and then
 * re-centred — exactly what `RoundedPolygon.toShape()` does with a static path.
 */
private class MorphOutlineShape(
    private val morph: Morph,
    private val progress: () -> Float,
) : Shape {
    private val polygonPath = Path()
    private val workPath = Path()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        polygonPath.rewind()
        morph.toPath(progress().coerceIn(0f, 1f), polygonPath)
        workPath.rewind()
        workPath.addPath(polygonPath)
        workPath.transform(Matrix().apply { scale(size.width, size.height) })
        workPath.translate(size.center - workPath.getBounds().center)
        return Outline.Generic(workPath)
    }
}
