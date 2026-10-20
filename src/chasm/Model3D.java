package chasm;

import java.io.DataInputStream;
import java.io.InputStream;

/**
 * 3D Polygonal Mesh representation for Chasm: The Rift (.CAR and .3O models).
 * Stores keyframe vertices, triangle indices, dismemberment body-part groups,
 * precomputed 3D face normals, Flat Shading polygon colors, and Vertex Colors / Painting.
 */
public final class Model3D {
    public int vCount;
    public int tCount;
    public int fCount;

    public byte[] triV0;
    public byte[] triV1;
    public byte[] triV2;
    public byte[] triGroup;
    public byte[] triNX;
    public byte[] triNY;
    public byte[] triNZ;

    // Flat Shading single color per polygon (packed 0xRRGGBB)
    public int[] triFlatRGB;

    // Vertex Colors / Vertex Painting per triangle corner (packed 0xRRGGBB)
    public int[] triVertRGB0;
    public int[] triVertRGB1;
    public int[] triVertRGB2;

    // Keyframe vertex data: frameVerts[frame][v * 3 + 0..2]
    public byte[][] frameVerts;

    public static Model3D[] loadAllModels(String resourcePath) {
        InputStream is = null;
        try {
            is = Model3D.class.getResourceAsStream(resourcePath);
            if (is == null) return new Model3D[0];
            DataInputStream dis = new DataInputStream(is);
            int numModels = dis.readUnsignedShort();
            Model3D[] models = new Model3D[numModels];
            for (int m = 0; m < numModels; m++) {
                Model3D mdl = new Model3D();
                int vc = dis.readUnsignedShort();
                int tc = dis.readUnsignedShort();
                int fc = dis.readUnsignedShort();
                mdl.vCount = vc;
                mdl.tCount = tc;
                mdl.fCount = fc;

                mdl.triV0 = new byte[tc];
                mdl.triV1 = new byte[tc];
                mdl.triV2 = new byte[tc];
                mdl.triGroup = new byte[tc];
                mdl.triNX = new byte[tc];
                mdl.triNY = new byte[tc];
                mdl.triNZ = new byte[tc];
                mdl.triFlatRGB = new int[tc];
                mdl.triVertRGB0 = new int[tc];
                mdl.triVertRGB1 = new int[tc];
                mdl.triVertRGB2 = new int[tc];

                for (int t = 0; t < tc; t++) {
                    mdl.triV0[t] = dis.readByte();
                    mdl.triV1[t] = dis.readByte();
                    mdl.triV2[t] = dis.readByte();
                    mdl.triGroup[t] = dis.readByte();
                    mdl.triNX[t] = dis.readByte();
                    mdl.triNY[t] = dis.readByte();
                    mdl.triNZ[t] = dis.readByte();

                    int fr = dis.readUnsignedByte();
                    int fg = dis.readUnsignedByte();
                    int fb = dis.readUnsignedByte();
                    mdl.triFlatRGB[t] = (fr << 16) | (fg << 8) | fb;

                    int r0 = dis.readUnsignedByte();
                    int g0 = dis.readUnsignedByte();
                    int b0 = dis.readUnsignedByte();
                    mdl.triVertRGB0[t] = (r0 << 16) | (g0 << 8) | b0;

                    int r1 = dis.readUnsignedByte();
                    int g1 = dis.readUnsignedByte();
                    int b1 = dis.readUnsignedByte();
                    mdl.triVertRGB1[t] = (r1 << 16) | (g1 << 8) | b1;

                    int r2 = dis.readUnsignedByte();
                    int g2 = dis.readUnsignedByte();
                    int b2 = dis.readUnsignedByte();
                    mdl.triVertRGB2[t] = (r2 << 16) | (g2 << 8) | b2;
                }

                mdl.frameVerts = new byte[fc][vc * 3];
                for (int f = 0; f < fc; f++) {
                    dis.readFully(mdl.frameVerts[f]);
                }
                models[m] = mdl;
            }
            return models;
        } catch (Exception e) {
            return new Model3D[0];
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ex) {}
            }
        }
    }
}
