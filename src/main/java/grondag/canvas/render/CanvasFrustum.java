package grondag.canvas.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.Matrix4f;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import grondag.canvas.chunk.BuiltRenderRegion;
import grondag.canvas.chunk.RegionChunkReference;
import grondag.canvas.mixinterface.Matrix4fExt;

/**
 * Plane equation derivations based on:
 * "Fast Extraction of Viewing Frustum Planes from the World- View-Projection Matrix"
 * Gill Gribb, Klaus Hartmann
 * https://www.gamedevs.org/uploads/fast-extraction-viewing-frustum-planes-from-world-view-projection-matrix.pdf
 *
 * AABB test method based on work by Ville Miettinen
 * as described in Real-Time Rendering, Fourth Edition (Page 971). CRC Press.
 * Abbey, Duane C.; Haines, Eric; Hoffman, Naty.
 */
@Environment(EnvType.CLIENT)
public class CanvasFrustum extends Frustum {
	private final Matrix4f mvpMatrix = new Matrix4f();
	private final Matrix4fExt lastProjectionMatrix = (Matrix4fExt)(Object) new Matrix4f();
	private final Matrix4fExt lastModelMatrix = (Matrix4fExt)(Object) new Matrix4f();
	private int viewVersion;
	private int positionVersion;

	private double lastViewX;
	private double lastViewY;
	private double lastViewZ;

	private float lastViewXf;
	private float lastViewYf;
	private float lastViewZf;

	private double lastPositionX;
	private double lastPositionY;
	private double lastPositionZ;

	// NB: distance (w) and subtraction are baked into region extents but must be done for other box tests
	private float leftX, leftY, leftZ, leftW, leftXe, leftYe, leftZe, leftRegionExtent, leftChunkExtent;
	private float rightX, rightY, rightZ, rightW, rightXe, rightYe, rightZe, rightRegionExtent, rightChunkExtent;
	private float topX, topY, topZ, topW, topXe, topYe, topZe, topRegionExtent, topChunkExtent;
	private float bottomX, bottomY, bottomZ, bottomW, bottomXe, bottomYe, bottomZe, bottomRegionExtent, bottomChunkExtent;
	private float nearX, nearY, nearZ, nearW, nearXe, nearYe, nearZe, nearRegionExtent, nearChunkExtent;

	private int viewDistanceSquared;

	public CanvasFrustum() {
		super(dummyMatrix(), dummyMatrix());
	}

	private static Matrix4f dummyMatrix() {
		final Matrix4f dummt = new Matrix4f();
		dummt.loadIdentity();
		return dummt;
	}

	/**
	 * Incremented when player moves more than 1 block.
	 * Triggers visibility rebuild and translucency resort.
	 */
	public int positionVersion() {
		return positionVersion;
	}

	/**
	 * Incremented when frustum changes for any reason by any amount - movement, rotation, etc.
	 */
	public int viewVersion() {
		return viewVersion;
	}

	public void prepare(Matrix4f modelMatrix, Matrix4f projectionMatrix, Camera camera) {
		final Vec3d vec = camera.getPos();
		final double x = vec.x;
		final double y = vec.y;
		final double z = vec.z;

		if(x == lastViewX && y == lastViewY && z == lastViewZ
				&& lastModelMatrix.matches(modelMatrix)
				&& lastProjectionMatrix.matches(projectionMatrix)) {
			return;
		}

		lastViewX = x;
		lastViewY = y;
		lastViewZ = z;

		lastViewXf = (float) x;
		lastViewYf = (float) y;
		lastViewZf = (float) z;

		lastModelMatrix.set(modelMatrix);
		lastProjectionMatrix.set(projectionMatrix);
		++viewVersion;

		mvpMatrix.loadIdentity();
		mvpMatrix.multiply(projectionMatrix);
		mvpMatrix.multiply(modelMatrix);

		extractPlanes();

		viewDistanceSquared = MinecraftClient.getInstance().options.viewDistance * 16;
		viewDistanceSquared *= viewDistanceSquared;

		final double dx = x - lastPositionX;
		final double dy = y - lastPositionY;
		final double dz = z - lastPositionZ;

		if (dx * dx + dy * dy + dz * dz > 1.0D) {
			++positionVersion;
			lastPositionX = x;
			lastPositionY = y;
			lastPositionZ = z;
		}
	}

	@Override
	public boolean isVisible(Box box) {
		final double x1 = box.x1;
		final double y1 = box.y1;
		final double z1 = box.z1;

		final float hdx = (float) (0.5 * (box.x2 - x1));
		final float hdy = (float) (0.5 * (box.y2 - y1));
		final float hdz = (float) (0.5 * (box.z2 - z1));

		assert hdx > 0;
		assert hdy > 0;
		assert hdz > 0;

		final float cx = (float) x1 + hdx - lastViewXf;
		final float cy = (float) y1 + hdy - lastViewYf;
		final float cz = (float) z1 + hdz - lastViewZf;

		if(cx * leftX + cy * leftY + cz * leftZ + leftW - (hdx * leftXe + hdy * leftYe + hdz * leftZe) > 0) {
			return false;
		}

		if(cx * rightX + cy * rightY + cz * rightZ + rightW - (hdx * rightXe + hdy * rightYe + hdz * rightZe) > 0) {
			return false;
		}

		if(cx * nearX + cy * nearY + cz * nearZ + nearW - (hdx * nearXe + hdy * nearYe + hdz * nearZe) > 0) {
			return false;
		}

		if(cx * topX + cy * topY + cz * topZ + topW - (hdx * topXe + hdy * topYe + hdz * topZe) > 0) {
			return false;
		}

		if(cx * bottomX + cy * bottomY + cz * bottomZ + bottomW - (hdx * bottomXe + hdy * bottomYe + hdz * bottomZe) > 0) {
			return false;
		}

		return true;
	}

	public boolean isRegionVisible(BuiltRenderRegion region) {
		final float cx = region.cameraRelativeCenterX;
		final float cy = region.cameraRelativeCenterY;
		final float cz = region.cameraRelativeCenterZ;

		if(cx * leftX + cy * leftY + cz * leftZ + leftRegionExtent > 0) {
			return false;
		}

		if(cx * rightX + cy * rightY + cz * rightZ + rightRegionExtent > 0) {
			return false;
		}

		if(cx * nearX + cy * nearY + cz * nearZ + nearRegionExtent > 0) {
			return false;
		}

		if(cx * topX + cy * topY + cz * topZ + topRegionExtent > 0) {
			return false;
		}

		if(cx * bottomX + cy * bottomY + cz * bottomZ + bottomRegionExtent > 0) {
			return false;
		}

		return true;
	}

	public boolean isChunkVisible(RegionChunkReference chunk) {
		final float cx = chunk.cameraRelativeCenterX;
		final float cy = chunk.cameraRelativeCenterY;
		final float cz = chunk.cameraRelativeCenterZ;

		if(cx * leftX + cy * leftY + cz * leftZ + leftChunkExtent > 0) {
			return false;
		}

		if(cx * rightX + cy * rightY + cz * rightZ + rightChunkExtent > 0) {
			return false;
		}

		if(cx * nearX + cy * nearY + cz * nearZ + nearChunkExtent > 0) {
			return false;
		}

		if(cx * topX + cy * topY + cz * topZ + topChunkExtent > 0) {
			return false;
		}

		if(cx * bottomX + cy * bottomY + cz * bottomZ + bottomChunkExtent > 0) {
			return false;
		}

		return true;
	}

	private void extractPlanes() {
		final Matrix4fExt matrix = (Matrix4fExt)(Object) mvpMatrix;
		final float a00 = matrix.a00();
		final float a01 = matrix.a01();
		final float a02 = matrix.a02();
		final float a03 = matrix.a03();
		final float a10 = matrix.a10();
		final float a11 = matrix.a11();
		final float a12 = matrix.a12();
		final float a13 = matrix.a13();
		final float a30 = matrix.a30();
		final float a31 = matrix.a31();
		final float a32 = matrix.a32();
		final float a33 = matrix.a33();

		float x  = a30 + a00;
		float y  = a31 + a01;
		float z  = a32 + a02;
		float w  = a33 + a03;
		float mag = -MathHelper.fastInverseSqrt(x * x + y * y + z * z);
		x *= mag;
		y *= mag;
		z *= mag;
		w *= mag;
		leftX = x;
		leftY = y;
		leftZ = z;
		leftW = w;
		float xe = Math.abs(x);
		float ye = Math.abs(y);
		float ze = Math.abs(z);
		leftXe = xe;
		leftYe = ye;
		leftZe = ze;
		leftRegionExtent = w - 8 * (xe + ye + ze);
		leftChunkExtent =  w - 8 * (xe + ze) - 128 * ye;

		x  = a30 - a00;
		y  = a31 - a01;
		z  = a32 - a02;
		w  = a33 - a03;
		mag = -MathHelper.fastInverseSqrt(x * x + y * y + z * z);
		x *= mag;
		y *= mag;
		z *= mag;
		w *= mag;
		rightX = x;
		rightY = y;
		rightZ = z;
		rightW = w;
		xe = Math.abs(x);
		ye = Math.abs(y);
		ze = Math.abs(z);
		rightXe = xe;
		rightYe = ye;
		rightZe = ze;
		rightRegionExtent = w - 8 * (xe + ye + ze);
		rightChunkExtent = w - 8 * (xe + ze) - 128 * ye;

		x  = a30 - a10;
		y  = a31 - a11;
		z  = a32 - a12;
		w  = a33 - a13;
		mag = -MathHelper.fastInverseSqrt(x * x + y * y + z * z);
		x *= mag;
		y *= mag;
		z *= mag;
		w *= mag;
		topX = x;
		topY = y;
		topZ = z;
		topW = w;
		xe = Math.abs(x);
		ye = Math.abs(y);
		ze = Math.abs(z);
		topXe = xe;
		topYe = ye;
		topZe = ze;
		topRegionExtent = w - 8 * (xe + ye + ze);
		topChunkExtent =  w - 8 * (xe + ze) - 128 * ye;

		x  = a30 + a10;
		y  = a31 + a11;
		z  = a32 + a12;
		w  = a33 + a13;
		mag = -MathHelper.fastInverseSqrt(x * x + y * y + z * z);
		x *= mag;
		y *= mag;
		z *= mag;
		w *= mag;
		bottomX = x;
		bottomY = y;
		bottomZ = z;
		bottomW = w;
		xe = Math.abs(x);
		ye = Math.abs(y);
		ze = Math.abs(z);
		bottomXe = xe;
		bottomYe = ye;
		bottomZe = ze;
		bottomRegionExtent = w - 8 * (xe + ye + ze);
		bottomChunkExtent =  w - 8 * (xe + ze) - 128 * ye;

		x  = a30 + matrix.a20();
		y  = a31 + matrix.a21();
		z  = a32 + matrix.a22();
		w  = a33 + matrix.a23();
		mag = -MathHelper.fastInverseSqrt(x * x + y * y + z * z);
		x *= mag;
		y *= mag;
		z *= mag;
		w *= mag;
		nearX = x;
		nearY = y;
		nearZ = z;
		nearW = w;
		xe = Math.abs(x);
		ye = Math.abs(y);
		ze = Math.abs(z);
		nearXe = xe;
		nearYe = ye;
		nearZe = ze;
		nearRegionExtent = w - 8 * (xe + ye + ze);
		nearChunkExtent = w - 8 * (xe + ze) - 128 * ye;
	}
}