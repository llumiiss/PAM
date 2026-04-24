package pl.pam.startproject.ui.launch

/** Krótki ekran startowy z animacją fade-in/fade-out. */
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import pl.pam.startproject.R

@Composable
fun LaunchImageScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Steruje przejściem alpha obrazka.
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "launchAlpha"
    )
    LaunchedEffect(Unit) {
        // Sekwencja: pojawienie -> chwila widoczności -> zniknięcie -> przejście dalej.
        visible = true
        delay(1500L)
        visible = false
        delay(500L)
        onFinished()
    }
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.launch_car),
            contentDescription = "Ekran startowy",
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            contentScale = ContentScale.Crop
        )
    }
}
