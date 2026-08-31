package com.github.noamm9.utils.render.world

import com.github.noamm9.utils.render.world.batches.FilledBatch
import com.github.noamm9.utils.render.world.batches.LineBatch
import com.github.noamm9.utils.render.world.batches.TextRenderState
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.util.LightCoordsUtil
import org.joml.Matrix4f
import org.joml.Vector3f

object RenderBatcher {
    private val filledBatches = mutableMapOf<net.minecraft.client.renderer.rendertype.RenderType, FilledBatch>()
    private val lineBatches = mutableMapOf<net.minecraft.client.renderer.rendertype.RenderType, LineBatch>()
    private val texts = ArrayList<TextRenderState>()

    val tmpVec = Vector3f()
    val tmpDir = Vector3f()

    fun filledBatch(phase: Boolean) = filledBatch(if (phase) NoammRenderLayers.FILLED_THROUGH_WALLS else NoammRenderLayers.FILLED)
    fun circleBatch(phase: Boolean) = filledBatch(if (phase) NoammRenderLayers.CIRCLE_FILLED_THROUGH_WALLS else NoammRenderLayers.CIRCLE_FILLED)
    fun lineBatch(phase: Boolean): LineBatch {
        val renderType = if (phase) NoammRenderLayers.LINES_THROUGH_WALLS else NoammRenderLayers.LINES
        return lineBatches.getOrPut(renderType) { LineBatch(renderType) }
    }

    internal fun addText(matrix: Matrix4f, text: String, xOff: Float, yOff: Float, argb: Int, seeThrough: Boolean) {
        texts.add(TextRenderState(Matrix4f(matrix), text, xOff, yOff, argb, seeThrough))
    }

    internal fun flush(context: LevelRenderContext) {
        if (filledBatches.isEmpty() && lineBatches.isEmpty() && texts.isEmpty()) return

        val collector = context.submitNodeCollector()
        val pendingFills = filledBatches.values.toList().also { filledBatches.clear() }
        val pendingLines = lineBatches.values.toList().also { lineBatches.clear() }
        val pendingTexts = texts.toList().also { texts.clear() }

        for (text in pendingTexts) {
            val poseStack = PoseStack()
            poseStack.last().pose().set(text.matrix)
            collector.submitText(
                poseStack,
                text.xOff,
                text.yOff,
                Component.literal(text.text).visualOrderText,
                true,
                if (text.seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL,
                LightCoordsUtil.FULL_BRIGHT,
                text.argb,
                0,
                0
            )
        }

        for (batch in pendingFills) {
            collector.submitCustomGeometry(PoseStack(), batch.renderType) { pose, buffer ->
                for (state in batch.data) buffer.addVertex(pose, state.x.toFloat(), state.y.toFloat(), state.z.toFloat())
                    .setColor(state.r, state.g, state.b, state.a)
            }
        }

        for (batch in pendingLines) {
            collector.submitCustomGeometry(PoseStack(), batch.renderType) { pose, buffer ->
                for (state in batch.data) buffer.addVertex(pose, state.x.toFloat(), state.y.toFloat(), state.z.toFloat())
                    .setColor(state.r, state.g, state.b, state.a)
                    .setNormal(pose, Vector3f(state.nx, state.ny, state.nz))
                    .setLineWidth(state.lineWidth)
            }
        }
    }

    private fun filledBatch(renderType: net.minecraft.client.renderer.rendertype.RenderType) =
        filledBatches.getOrPut(renderType) { FilledBatch(renderType) }
}
