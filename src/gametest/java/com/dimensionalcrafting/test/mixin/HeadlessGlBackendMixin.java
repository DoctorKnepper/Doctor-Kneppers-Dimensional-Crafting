package com.dimensionalcrafting.test.mixin;

import com.mojang.renderpearl.backend.opengl.GlBackend;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Test-only: the software GL on headless CI (Mesa llvmpipe under Xvfb) exposes no sRGB-capable
 * visuals, so the client can't open a window. Skip that one request so client game tests can run.
 * Lives in the test source set only; never part of the released jar.
 */
@Mixin(GlBackend.class)
public class HeadlessGlBackendMixin {
	@Redirect(
		method = "createWindow",
		at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SetAttribute(II)Z", remap = false),
		require = 0
	)
	private boolean dimensionalcrafting$skipSrgbOnHeadless(final int attribute, final int value) {
		if (attribute == SDLVideo.SDL_GL_FRAMEBUFFER_SRGB_CAPABLE && System.getenv("DIMENSIONAL_CRAFTING_HEADLESS_GL") != null) {
			return true;
		}

		return SDLVideo.SDL_GL_SetAttribute(attribute, value);
	}
}
