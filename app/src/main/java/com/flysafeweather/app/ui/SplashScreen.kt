package com.flysafeweather.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.flysafeweather.app.R

@Composable
fun SplashScreen() {
    Image(
        painter = painterResource(id = R.drawable.flysafe_splash),
        contentDescription = "FlySafe Weather",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}
