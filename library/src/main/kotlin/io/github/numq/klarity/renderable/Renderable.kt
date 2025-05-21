package io.github.numq.klarity.renderable

import io.github.numq.klarity.renderer.Renderer

interface Renderable {
    /**
     * Returns attached renderers.
     *
     * @return [Result] containing a list of attached renderers
     */
    suspend fun getRenderers(): Result<List<Renderer>>

    /**
     * Attaches a renderer to display preview frames.
     *
     * @param renderer the renderer implementation that will receive video frames
     *
     * @return [Result] indicating success
     */
    suspend fun attachRenderer(renderer: Renderer): Result<Unit>

    /**
     * Detaches the renderer if it is attached.
     *
     * @return [Result] indicating success
     */
    suspend fun detachRenderer(renderer: Renderer): Result<Unit>

    /**
     * Detaches all attached renderers.
     *
     * @return [Result] indicating success
     */
    suspend fun detachRenderers(): Result<Unit>
}