# Полное руководство по Material Design 3 Expressive в Jetpack Compose

## 1. Философия и фундамент Material 3 Expressive

Эволюция дизайн-системы Android отражает переход от строгой алгоритмической унификации к эмоциональному, тактильному и кинематографичному взаимодействию. 

Представленный в Android 12 оригинальный **Material You (Material Design 3)** заложил основу адаптивности за счет динамических цветов Monet. Однако на практике интерфейс страдал от визуальной монотонности: пастельные оттенки с низкой насыщенностью приводили к недостатку контраста, плоские поверхности сливались на экранах разной калибровки, а анимации на базе интерполяторов Безье казались механистическими и «залипали» при прерывании жестов пользователем.

Начиная с **Android 15, 16 и 17**, Google внедряет **Material 3 Expressive** — результат более чем 46 масштабных исследовательских циклов и тестов на десятках тысяч пользователей.

### Ключевые столпы Expressive-дизайна

1. **Эмоциональный отклик (Delight & Whimsy):** Уход от серой стерильности. Интерфейс наполняется энергичными формами, выразительными скруглениями, переменным ритмом и визуальным теплом.
2. **Физическая аутентичность (Natural Spring Physics):** Полный отказ от фиксированных временных интервалов (ms) и кривых Безье в пользу динамических дифференциальных уравнений пружин, где масса, жесткость и затухание бесшовно передают кинетическую энергию жеста пальца.
3. **Контрастная многослойность (Autonomous Surface Elevation):** Отказ от грязно-серых наложений полупрозрачного `surfaceTint` в пользу четко разделенной 5-уровневой системы контейнеров `SurfaceContainer` с выверенным шагом светлоты (Luminance) в пространстве HCT.
4. **Вариативная и журнальная типографика (Editorial Typography & Variable Fonts):** Акцентированные, плотные заголовки с динамическим изменением оптических осей шрифтов (`wght`, `wdth`, `opsz`, `GRAD`) в ответ на интерактивные действия.
5. **Сопряженная эргономика форм (Connected Shapes):** Замена сегментированных переключателей на физически соединенные кнопки (`SplitButtonLayout`, `ButtonGroup`) с реактивным скруглением углов в моменты нажатий.
6. **Обязательный сквозной рендеринг (Edge-to-Edge by Default):** Полная интеграция интерфейса в системное пространство без фальшивых подложек статус-бара, глубокая поддержка предиктивного жеста возврата (Predictive Back Gesture).

---

## 2. Сравнительная матрица: M3 vs M3 Expressive

| Параметр спецификации | Базовый Material 3 (Android 12–14) | Material 3 Expressive (Android 15–17) |
| :--- | :--- | :--- |
| **Геометрия кнопок** | Симметричное фиксированное скругление 12–20 dp | Скругления 16–28 dp, реактивные сопряженные формы (`CornerShapes`) |
| **Геометрия карточек** | Скругление 12–16 dp, малая глубина | Скругление до 28–32 dp, глубина через контраст слоев и выраженные рамки |
| **Цветовой движок** | Модель CAM16, пастельные тона 2021 г. | Модель HCT, движок `ColorSpec 2025`, насыщенный стиль `Expressive` |
| **Возвышение (Elevation)** | Тонирование `surfaceTint` поверх подложки | 5 дискретных уровней `SurfaceContainer` без искусственных наложений |
| **Физика анимаций** | `CubicBezierEasing`, фиксированное время | Пружины `SpringSpec`, `MotionScheme`, учет скорости жеста |
| **Группы действий** | Сегментированные кнопки (Segmented Buttons) | `SplitButtonLayout`, соединенные `ButtonGroup` с общим контуром |
| **Панели инструментов** | Статичный нижний `BottomAppBar` | Парящий над контентом `HorizontalFloatingToolbar` со скролл-эффектом |
| **Списочные структуры** | Стандартные горизонтальные Row/Pager | `HorizontalMultiBrowseCarousel` с непрерывным масштабированием |
| **Системная интеграция** | Опциональный сквозной рендеринг | Принудительный Edge-to-Edge, аппаратный `PredictiveBackHandler` |

---

## 3. Математика цвета и контейнерная модель

### Цветовое пространство HCT и ColorSpec 2025

Material You опирается на пространство **HCT** (*Hue, Chroma, Tone*):
* **Hue (Оттенок):** Цветовой тон от $0^\circ$ до $360^\circ$.
* **Chroma (Насыщенность):** Чистота и интенсивность цвета (в спецификации Expressive увеличена на 15–30% относительно базовой палитры 2021 года).
* **Tone (Светлота):** Перцептивная яркость от $0$ (абсолютно черный) до $100$ (абсолютно белый). Гарантирует соблюдение контрастности по WCAG 2.1 / WCAG 3.0 (APCA) независимо от выбранного базового оттенка.

### Архитектура 5-уровневых контейнеров SurfaceContainer

В темной теме старый механизм `surfaceTint` делал темные оттенки неестественно белесыми. Новая архитектура использует дискретные слои с математически калиброванным тоном:

```
[Экран/AMOLED фон]  ->  surfaceContainerLowest (Тон 4 в Dark / Тон 100 в Light)
[Фоновые секции]    ->  surfaceContainerLow    (Тон 10 в Dark / Тон 96 в Light)
[Базовые карточки]  ->  surfaceContainer       (Тон 12 в Dark / Тон 94 в Light)
[Всплывающие меню]  ->  surfaceContainerHigh   (Тон 17 в Dark / Тон 92 в Light)
[Активные инпуты]   ->  surfaceContainerHighest(Тон 22 в Dark / Тон 90 в Light)
```

### Кодовая реализация цветовой схемы

```kotlin
package com.example.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Светлая палитра Expressive
private val PrimaryLight = Color(0xFF005AC1)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val PrimaryContainerLight = Color(0xFFD8E2FF)
private val OnPrimaryContainerLight = Color(0xFF001A41)

private val SecondaryLight = Color(0xFF535F70)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFD7E3F7)
private val OnSecondaryContainerLight = Color(0xFF101C2B)

private val TertiaryLight = Color(0xFF705574)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFFAD8FD)
private val OnTertiaryContainerLight = Color(0xFF28132E)

private val SurfaceLight = Color(0xFFF9F9FF)
private val OnSurfaceLight = Color(0xFF191C20)
private val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
private val SurfaceContainerLowLight = Color(0xFFF3F3FA)
private val SurfaceContainerLight = Color(0xFFEDEDF4)
private val SurfaceContainerHighLight = Color(0xFFE7E8EE)
private val SurfaceContainerHighestLight = Color(0xFFE2E2E9)

// Темная палитра Expressive
private val PrimaryDark = Color(0xFFADC6FF)
private val OnPrimaryDark = Color(0xFF002E69)
private val PrimaryContainerDark = Color(0xFF004494)
private val OnPrimaryContainerDark = Color(0xFFD8E2FF)

private val SecondaryDark = Color(0xFFBBC7DB)
private val OnSecondaryDark = Color(0xFF253140)
private val SecondaryContainerDark = Color(0xFF3B4858)
private val OnSecondaryContainerDark = Color(0xFFD7E3F7)

private val TertiaryDark = Color(0xFFDDBCE0)
private val OnTertiaryDark = Color(0xFF3F2844)
private val TertiaryContainerDark = Color(0xFF573E5B)
private val OnTertiaryContainerDark = Color(0xFFFAD8FD)

private val SurfaceDark = Color(0xFF111318)
private val OnSurfaceDark = Color(0xFFE2E2E9)
private val SurfaceContainerLowestDark = Color(0xFF0C0E13)
private val SurfaceContainerLowDark = Color(0xFF191C20)
private val SurfaceContainerDark = Color(0xFF1D2024)
private val SurfaceContainerHighDark = Color(0xFF282A2F)
private val SurfaceContainerHighestDark = Color(0xFF33353A)

val ExpressiveLightColorScheme: ColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight
)

val ExpressiveDarkColorScheme: ColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark
)

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun provideExpressiveColorScheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    context: Context = LocalContext.current
): ColorScheme {
    return when {
        dynamicColor && supportsDynamicColor() -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ExpressiveDarkColorScheme
        else -> ExpressiveLightColorScheme
    }
}
```

---

## 4. Типографика и вариативная пластика шрифтов

### Журнальная шкала Expressive Typography

В M3 Expressive заголовки становятся акцентированнее (`FontWeight.Bold` вместо `Medium`), а интерлиньяж (line height) увеличен для идеальной читаемости на экранах с высокой плотностью пикселей и складных устройствах.

```
Display Large:   57 sp / 64 sp line-height / Weight 700 / Tracking -0.25 sp
Display Medium:  45 sp / 52 sp line-height / Weight 700 / Tracking  0.00 sp
Headline Large:  32 sp / 40 sp line-height / Weight 700 / Tracking  0.00 sp
Headline Medium: 28 sp / 36 sp line-height / Weight 600 / Tracking  0.00 sp
Title Large:     22 sp / 28 sp line-height / Weight 500 / Tracking  0.00 sp
Body Large:      16 sp / 24 sp line-height / Weight 400 / Tracking +0.50 sp
Label Large:     14 sp / 20 sp line-height / Weight 600 / Tracking +0.10 sp
```

### Вариативные шрифты (Variable Fonts)

Вместо упаковки 8 отдельных файлов TTF/OTF вариативный шрифт (например, `Roboto Flex` или `Google Sans Flex`) использует один оптимизированный файл и аппаратную интерполяцию по осям:
* `wght` — вес (от 100 до 1000).
* `wdth` — ширина (от 25% до 150%).
* `slnt` — наклон.
* `opsz` — оптический размер кегля.

```kotlin
package com.example.designsystem.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.designsystem.R

@OptIn(ExperimentalTextApi::class)
fun createVariableFontFamily(
    weight: Int = 400,
    width: Float = 100f,
    slant: Float = 0f
): FontFamily {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        FontFamily(
            Font(
                resId = R.font.roboto_flex_variable,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.width(width),
                    FontVariation.slant(slant)
                )
            )
        )
    } else {
        FontFamily.Default
    }
}

val ExpressiveTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = createVariableFontFamily(weight = 700),
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = createVariableFontFamily(weight = 700),
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = createVariableFontFamily(weight = 600),
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = createVariableFontFamily(weight = 500),
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = createVariableFontFamily(weight = 400),
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = createVariableFontFamily(weight = 600),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)
```

---

## 5. Кинематика, физика пружин и `MotionScheme`

### Дифференциальная физика против кривых Безье

Кривые Безье с фиксированным временем выполнения ($t$ в миллисекундах) создают эффект «стеклянного экрана». Если пользователь касается экрана во время анимации, фиксированная кривая сбрасывает скорость в ноль, вызывая визуальный рывок (jank).

Material 3 Expressive описывает все движения дифференциальным уравнением затухающих колебаний:

$$m \frac{d^2x}{dt^2} + c \frac{dx}{dt} + k x = 0$$

* $m$ — виртуальная масса объекта.
* $k$ — коэффициент жесткости (`stiffness`).
* $c$ — коэффициент затухания (`dampingRatio`).

При перехвате элемента пальцем накопленный вектор скорости $\vec{v} = \frac{dx}{dt}$ сохраняется и передается в новое состояние без потери кадров даже на частоте 120 Гц.

### Архитектура MotionScheme в Compose

Начиная с `material3:1.4.0+`, глобальная физика управляется через интерфейс `MotionScheme`. Все компоненты (диалоги, кнопки, шторы, списки) считывают физику из `MaterialExpressiveTheme`.

| Спецификация | Класс анимации | Параметры пружины | Поведение и сценарий |
| :--- | :--- | :--- | :--- |
| `fastSpatialSpec` | Пространство (размер, позиция) | `stiffness = 10000f`, `damping = 0.80f` | Микро-отскок при клике на иконки и кнопки |
| `defaultSpatialSpec` | Пространство (размер, позиция) | `stiffness = 400f`, `damping = 0.75f` | Выраженный overshoot при раскрытии карточек |
| `slowSpatialSpec` | Пространство (размер, позиция) | `stiffness = 200f`, `damping = 0.70f` | Мягкое появление штор и модальных окон |
| `fastEffectsSpec` | Эффект (альфа, цвет) | `stiffness = 10000f`, `damping = 1.0f` | Мгновенный отклик подсветки при тапе |
| `defaultEffectsSpec` | Эффект (альфа, цвет) | `stiffness = 1500f`, `damping = 1.0f` | Смена оттенка фона без колебаний |
| `slowEffectsSpec` | Эффект (альфа, цвет) | `stiffness = 200f`, `damping = 1.0f` | Плавное затемнение подложки (Scrim) |

```kotlin
package com.example.designsystem.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun createExpressiveMotionScheme(): MotionScheme = object : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessHigh)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessLow)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
}
```

---

## 6. Продвинутые паттерны: Shared Transitions и Predictive Back

### Бесшовный переход с сохранением геометрии через SharedTransitionLayout

В Expressive-стиле контент не «мигает» между экранами. Контейнер плавно вырастает из превью в карточку детального экрана, сохраняя скругления и фокус внимания.

```kotlin
package com.example.designsystem.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExpressiveSharedNavigation() {
    val navController = rememberNavController()

    SharedTransitionLayout {
        NavHost(navController = navController, startDestination = "feed") {
            composable("feed") {
                FeedScreen(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                    onOpenDetails = { id -> navController.navigate("details/$id") }
                )
            }
            composable("details/{id}") { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("id").orEmpty()
                DetailScreen(
                    itemId = itemId,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FeedScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenDetails: (String) -> Unit
) {
    with(sharedTransitionScope) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text("Обзор проектов", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .sharedElement(
                        state = rememberSharedContentState(key = "card_hero_1"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onOpenDetails("1") }
                    .padding(20.dp)
            ) {
                Text(
                    "Интерактивная карта Expressive",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DetailScreen(
    itemId: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClose: () -> Unit
) {
    with(sharedTransitionScope) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClose() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .sharedElement(
                        state = rememberSharedContentState(key = "card_hero_1"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(24.dp)
            ) {
                Text(
                    "Интерактивная карта Expressive #$itemId",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                "Детальное описание с непрерывным переходом контекста",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}
```

### Предиктивный жест «Назад» (Predictive Back Handler)

В Android 15+ жест возврата не прерывает экран внезапно, а плавно стягивает его пропорционально смещению пальца, закругляя углы до 28 dp и обнажая предыдущий слой. При отмене жеста сработает восстанавливающая пружина `animateTo(1f)`.

```kotlin
package com.example.designsystem.motion

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun PredictiveBackContainer(
    enabled: Boolean = true,
    onBackConfirmed: () -> Unit,
    content: @Composable () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val cornerRadius = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    PredictiveBackHandler(enabled = enabled) { progressFlow: Flow<BackEventCompat> ->
        try {
            progressFlow.collect { event ->
                // Мягкое масштабирование экрана до 90% и увеличение радиуса скругления
                val progress = event.progress
                scale.snapTo(1f - (progress * 0.10f))
                cornerRadius.snapTo(progress * 28f)
            }
            onBackConfirmed()
        } catch (e: CancellationException) {
            // Пользователь передумал: откат с пружинной физикой
            scope.launch {
                scale.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 600f))
                cornerRadius.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 600f))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                shape = RoundedCornerShape(cornerRadius.value.dp)
                clip = true
            }
    ) {
        content()
    }
}
```

---

## 7. Каталог компонентов Material 3 Expressive

### Соединенные кнопки: SplitButtonLayout и ButtonGroup

Сегментированные кнопки признаны визуально устаревшими. Их заменяют связанные группы кнопок с динамическими сопряжениями форм.

```kotlin
package com.example.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveButtonGroupSample() {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableIntStateOf(0) }
    val periods = listOf("День", "Неделя", "Месяц")

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Разделенная кнопка (Split Button)
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = { /* Основное действие */ }
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Отправить")
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = isExpanded,
                    onCheckedChange = { isExpanded = it }
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Дополнительные опции")
                }
            }
        )

        // Связанная группа селекторов (Connected Button Group)
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            periods.forEachIndexed { index, title ->
                val isSelected = selectedPeriod == index
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { selectedPeriod = index },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        periods.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSpacing)
                        )
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    }
                    Text(title)
                }
            }
        }
    }
}
```

### Парящая панель инструментов: HorizontalFloatingToolbar

Панель не прибита к нижней кромке экрана, а мягко парит над контентом с радиусом скругления контейнера 28 dp, отбрасывая аккуратную тень.

```kotlin
package com.example.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingToolbarScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(30) { index ->
                ListItem(
                    headlineContent = { Text("Элемент каталога #$index") },
                    supportingContent = { Text("Высокоскоростная отрисовка 120fps") }
                )
            }
        }

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
            content = {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "Нравится")
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = "Сохранить")
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Share, contentDescription = "Поделиться")
                }
                FilledIconButton(onClick = {}) {
                    Icon(Icons.Default.Add, contentDescription = "Создать")
                }
            }
        )
    }
}
```

### Динамическая карусель: HorizontalMultiBrowseCarousel

В отличие от стандартного `HorizontalPager`, `HorizontalMultiBrowseCarousel` автоматически интерполирует масштаб элементов: центральная карточка раскрывается целиком, а края соседних аккуратно выглядывают, подсказывая возможность прокрутки.

```kotlin
package com.example.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveCarouselSection(items: List<String>) {
    val carouselState = rememberCarouselState { items.size }

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 240.dp,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(vertical = 12.dp)
    ) { pageIndex ->
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(20.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = items[pageIndex],
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
```

---

## 8. Адаптивная навигация: NavigationSuiteScaffold

Компонент `NavigationSuiteScaffold` исключает необходимость вручную писать ветвления для смартфонов, планшетов и десктопов:
* На смартфонах (`Compact` < 600 dp) интерфейс отрисовывает нижний `NavigationBar`.
* На складных устройствах и небольших планшетах (`Medium` 600–840 dp) превращается в боковой рельс `NavigationRail`.
* На больших экранах (`Expanded` > 840 dp) раскрывается в постоянный `NavigationDrawer`.

```kotlin
package com.example.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ExpressiveAdaptiveApp() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val navigationItems = listOf("Обзор", "Поиск", "Настройки")
    val icons = listOf(Icons.Default.Dashboard, Icons.Default.Explore, Icons.Default.Settings)

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            navigationItems.forEachIndexed { index, title ->
                item(
                    icon = { Icon(icons[index], contentDescription = title) },
                    label = { Text(title) },
                    selected = selectedIndex == index,
                    onClick = { selectedIndex = index }
                )
            }
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Раздел: ${navigationItems[selectedIndex]}",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
```

---

## 9. Конфигурация сборки и обязательный Edge-to-Edge

### Каталог версий `libs.versions.toml`

Для доступа к API Expressive требуются свежие версии библиотек (ветка `1.4.0+` или `1.5.0-alpha`).

```toml
[versions]
agp = "8.8.0"
kotlin = "2.1.0"
androidxCore = "1.15.0"
androidxActivity = "1.10.0"
androidxLifecycle = "2.8.7"
composeBom = "2025.02.00"
composeMaterial3 = "1.4.0-alpha14"
composeMaterial3Adaptive = "1.0.0"
composeNavigation = "2.8.7"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "androidxCore" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "androidxActivity" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "androidxLifecycle" }

compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-animation = { group = "androidx.compose.animation", name = "animation" }

androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "composeMaterial3" }
androidx-compose-material3-adaptive = { group = "androidx.compose.material3.adaptive", name = "adaptive-navigation-suite", version.ref = "composeMaterial3Adaptive" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "composeNavigation" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### Конфигурация модуля `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.expressivedesign"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.expressivedesign"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.animation)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.navigation.compose)
}
```

### Сквозной рендеринг: `MainActivity.kt`

Начиная с Android 15, флаг `enableEdgeToEdge()` активирует принудительную прозрачность системных плашек. Приложение обязано обрабатывать системные отступы `WindowInsets` через контейнеры `Scaffold`.

```kotlin
package com.example.expressivedesign

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.designsystem.components.ExpressiveAdaptiveApp
import com.example.designsystem.theme.MaterialExpressiveAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Активация сквозной полноэкранной отрисовки без подложек
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MaterialExpressiveAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ExpressiveAdaptiveApp()
                }
            }
        }
    }
}
```

---

## 10. Корневой контейнер MaterialExpressiveTheme

Контейнер `MaterialExpressiveTheme` объединяет динамическую палитру, обновленные радиусы форм `Shapes`, вариативную типографику и физическую схему `MotionScheme.expressive()`.

```kotlin
package com.example.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialExpressiveAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = provideExpressiveColorScheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
```

---

## 11. Модульная архитектура промышленного уровня

По эталонной архитектуре Google (*Now in Android* и *Androidify*), дизайн-система изолируется в модуль `:core:designsystem`:

```
:app
 └── presentation / navigation
:feature:*
 └── screens (потребляют токены исключительно через MaterialTheme)
:core:designsystem
 ├── theme
 │    ├── MaterialExpressiveAppTheme.kt
 │    └── Shapes.kt
 ├── color
 │    ├── ColorScheme.kt
 │    └── DynamicMonet.kt
 ├── typography
 │    ├── VariableTypography.kt
 │    └── FontAxes.kt
 ├── motion
 │    ├── ExpressiveMotionScheme.kt
 │    └── PredictiveTransitions.kt
 └── components
      ├── Buttons.kt (SplitButton, ButtonGroup)
      ├── Toolbars.kt (FloatingToolbar)
      └── Carousels.kt (MultiBrowseCarousel)
```

### Архитектурные правила чистоты UI:
1. **Никаких Hardcoded-значений:** Запрещено задавать `Color(0xFF...)` или `dp` напрямую в экранах модулей фич. Все цвета считываются из `MaterialTheme.colorScheme.*`, радиусы — из `MaterialTheme.shapes.*`.
2. **UDF (Unidirectional Data Flow):** Состояние экранов упаковывается в `@Immutable data class`, а события передаются наверх через изолированные лямбда-коллбэки.
3. **Стабильность рекомпозиции:** Все коллекции в состояниях экранов должны быть обернуты в `kotlinx.collections.immutable.ImmutableList` для гарантии частоты 120 FPS.

---

## 12. Чек-лист соответствия «Expressive Excellence»

* [ ] **Edge-to-Edge:** Вызван `enableEdgeToEdge()` в `MainActivity.onCreate()`.
* [ ] **Контейнеры поверхностей:** Никаких `surfaceTint`. Все фоны используют токены `surfaceContainerLowest ... Highest`.
* [ ] **Физика пружин:** В теме установлен `motionScheme = MotionScheme.expressive()`.
* [ ] **Predictive Back:** Реализован плавный жест возврата через `PredictiveBackHandler`.
* [ ] **Соединенные кнопки:** Сегментированные переключатели заменены на `ButtonGroup` и `SplitButtonLayout`.
* [ ] **Парящие элементы:** Используются `HorizontalFloatingToolbar` и динамические `Carousel`.
* [ ] **Вариативные шрифты:** Подключен `Google Sans Flex` или `Roboto Flex` с оптической компенсацией кегля.
* [ ] **Адаптивность:** Навигация построена на `NavigationSuiteScaffold`.