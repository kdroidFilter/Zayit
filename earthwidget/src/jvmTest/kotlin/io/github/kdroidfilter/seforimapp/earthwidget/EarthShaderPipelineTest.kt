package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.test.Test
import kotlin.test.assertNotNull

class EarthShaderPipelineTest {
    @Test
    fun shaderCompiles() {
        val pipeline = EarthShaderPipeline.create()
        assertNotNull(pipeline, "SkSL sphere shader failed to compile — check EarthShaders.kt syntax")
    }
}
