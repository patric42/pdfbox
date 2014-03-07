/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.pdmodel.graphics.image;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDIndexed;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Reads a sampled image from a PDF file.
 * @author John Hewson
 */
final class SampledImageReader
{
    private static final Log LOG = LogFactory.getLog(SampledImageReader.class);

    /**
     * Returns an ARGB image filled with the given paint and using the given image as a mask.
     * @param paint the paint to fill the visible portions of the image with
     * @return a masked image filled with the given paint
     * @throws IOException if the image cannot be read
     * @throws IllegalStateException if the image is not a stencil.
     */
    public static BufferedImage getStencilImage(PDImage pdImage, Paint paint) throws IOException
    {
        // get mask (this image)
        BufferedImage mask = getRGBImage(pdImage, null);
        // compose to ARGB
        BufferedImage masked = new BufferedImage(mask.getWidth(), mask.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = masked.createGraphics();

        // draw the mask
        //g.drawImage(mask, 0, 0, null);

        // fill with paint using src-in
        //g.setComposite(AlphaComposite.SrcIn);
        g.setPaint(paint);
        g.fillRect(0, 0, mask.getWidth(), mask.getHeight());
        g.dispose();

        // set the alpha
        int width = masked.getWidth();
        int height = masked.getHeight();
        WritableRaster raster = masked.getRaster();
        WritableRaster alpha = mask.getRaster();

//        int[] rgba = new int[4];
        final int[] transparent = new int[4];
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
//                raster.getPixel(x, y, rgba);

                if (alpha.getPixel(x, y, (int[])null)[0] == 255)
                {
                    raster.setPixel(x, y, transparent);
                }
//                else
//                {
//                    raster.setPixel(x, y, rgba);
//                }
            }
        }

        return masked;
    }

    /**
     * Returns the content of the given image as an AWT buffered image with an RGB color space.
     * If a color key mask is provided then an ARGB image is returned instead.
     * This method never returns null.
     * @param pdImage the image to read
     * @param colorKey an optional color key mask
     * @return content of this image as an RGB buffered image
     * @throws IOException if the image cannot be read
     */
    public static BufferedImage getRGBImage(PDImage pdImage, COSArray colorKey) throws IOException
    {
        if (pdImage.getStream().getLength() == 0)
        {
            throw new IOException("Image stream is empty");
        }

        // get parameters, they must be valid or have been repaired
        final PDColorSpace colorSpace = pdImage.getColorSpace();
        final int numComponents = colorSpace.getNumberOfComponents();
        final int width = pdImage.getWidth();
        final int height = pdImage.getHeight();
        final int bitsPerComponent = pdImage.getBitsPerComponent();
        final float[] decode = getDecodeArray(pdImage);
        

        //
        // An AWT raster must use 8/16/32 bits per component. Images with < 8bpc
        // will be unpacked into a byte-backed raster. Images with 16bpc will be reduced
        // in depth to 8bpc as they will be drawn to TYPE_INT_RGB images anyway. All code
        // in PDColorSpace#toRGBImage expects and 8-bit range, i.e. 0-255.
        //
        WritableRaster raster = Raster.createBandedRaster(DataBuffer.TYPE_BYTE, width, height,
                numComponents, new Point(0, 0));

        // read bit stream
        ImageInputStream iis = null;
        try
        {
          // create stream
           iis = new MemoryCacheImageInputStream(pdImage.getStream().createInputStream());
           final float sampleMax = (float)Math.pow(2, bitsPerComponent) - 1f;
           final boolean isIndexed = colorSpace instanceof PDIndexed;
           byte[] srcColorValues = new byte[numComponents];
           byte[] alpha = new byte[1];

           //on 1bit images, skip the color conversion..
           if (numComponents == 1 && bitsPerComponent == 1) {
              int rowlen = (width + 7) / 8;
              byte[] buff = new byte[rowlen * height];
              iis.readFully(buff);

              for (int y = 0; y < height; y++) {
                 for (int x = 0; x < width; x++) {
                    //boolean isMasked = true;
                    int boff = x >> 3;
                    int bitmask = 1 << (7 - (x & 7));
                    int value = (buff[y * rowlen + boff] & bitmask) > 0 ? 1 : 0;

                    // decode array
                    final float dMin = decode[0];
                    final float dMax = decode[1];

                    // interpolate to domain
                    float output = dMin + (value * ((dMax - dMin) / sampleMax));

                    if (isIndexed) {
                       // indexed color spaces get the raw value, because the TYPE_BYTE
                       // below cannot be reversed by the color space without it having
                       // knowledge of the number of bits per component
                       srcColorValues[0] = (byte) Math.round(output);
                    } else {
                       // interpolate to TYPE_BYTE
                       int outputByte = Math.round(((output - Math.min(dMin, dMax))
                               / Math.abs(dMax - dMin)) * 255f);
                       srcColorValues[0] = (byte) outputByte;
                    }
                    raster.setDataElements(x, y, srcColorValues);
                 }
              }

              ColorModel colorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
              BufferedImage src = new BufferedImage(colorModel, raster, false, null);
              return src;
           }
           
           //original code
            // init color key mask
            float[] colorKeyRanges = null;
            BufferedImage colorKeyMask = null;
            if (colorKey != null)
            {
                colorKeyRanges = colorKey.toFloatArray();
                colorKeyMask = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            }

            // calculate row padding
            int padding = 0;
            if (width * numComponents * bitsPerComponent % 8 > 0)
            {
                padding = 8 - (width * numComponents * bitsPerComponent % 8);
            }

            // read stream

            
            ////////////OLD///////////////
            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    boolean isMasked = true;
                    for (int c = 0; c < numComponents; c++)
                    {
                        int value = (int)iis.readBits(bitsPerComponent);

                        // color key mask requires values before they are decoded
                        if (colorKeyRanges != null)
                        {
                            isMasked &= value >= colorKeyRanges[c * 2] &&
                                        value <= colorKeyRanges[c * 2 + 1];
                        }

                        // decode array
                        final float dMin = decode[c * 2];
                        final float dMax = decode[(c * 2) + 1];

                        // interpolate to domain
                        float output = dMin + (value * ((dMax - dMin) / sampleMax));

                        if (isIndexed)
                        {
                            // indexed color spaces get the raw value, because the TYPE_BYTE
                            // below cannot be reversed by the color space without it having
                            // knowledge of the number of bits per component
                            srcColorValues[c] = (byte)Math.round(output);
                        }
                        else
                        {
                            // interpolate to TYPE_BYTE
                            int outputByte = Math.round(((output - Math.min(dMin, dMax)) /
                                    Math.abs(dMax - dMin)) * 255f);

                            srcColorValues[c] = (byte)outputByte;
                        }
                    }
                    raster.setDataElements(x, y, srcColorValues);

                    // set alpha channel in color key mask, if any
                    if (colorKeyMask != null)
                    {
                        alpha[0] = (byte)(isMasked ? 255 : 0);
                        colorKeyMask.getRaster().setDataElements(x, y, alpha);
                    }
                }

                // rows are padded to the nearest byte
                iis.readBits(padding);
            }
            
            
            
            /////////////////////////////////////7

            // use the color space to convert the image to RGB
            BufferedImage rgbImage = colorSpace.toRGBImage(raster);
            // apply color mask, if any
            if (colorKeyMask != null)
            {
                return applyColorKeyMask(rgbImage, colorKeyMask);
            }
            else
            {
                return rgbImage;
            }
        }
        finally
        {
            if (iis != null)
            {
                iis.close();
            }
        }
    }

    // color key mask: RGB + Binary -> ARGB
    private static BufferedImage applyColorKeyMask(BufferedImage image, BufferedImage mask)
            throws IOException
    {
        int width = image.getWidth();
        int height = image.getHeight();

        // compose to ARGB
        BufferedImage masked = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        WritableRaster src = image.getRaster();
        WritableRaster dest = masked.getRaster();
        WritableRaster alpha = mask.getRaster();

        float[] rgb = new float[3];
        float[] rgba = new float[4];
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                src.getPixel(x, y, rgb);

                rgba[0] = rgb[0];
                rgba[1] = rgb[1];
                rgba[2] = rgb[2];
                rgba[3] = 255 - alpha.getPixel(x, y, (float[])null)[0];

                dest.setPixel(x, y, rgba);
            }
        }

        return masked;
    }

    // gets decode array from dictionary or returns default
    private static float[] getDecodeArray(PDImage pdImage) throws IOException
    {
        final COSArray cosDecode = pdImage.getDecode();
        float[] decode = null;

        if (cosDecode != null)
        {
            decode = cosDecode.toFloatArray();

            // if ImageMask is true then decode must be [0 1] or [1 0]
            if (pdImage.isStencil() && (decode.length != 2 ||
                decode[0] < 0 || decode[0] > 1 ||
                decode[1] < 0 || decode[1] > 1))
            {
                LOG.warn("Ignored invalid decode array: not compatible with ImageMask");
                decode = null;
            }

            // otherwise, its length shall be twice the number of colour
            // components required by ColorSpace
            int n = pdImage.getColorSpace().getNumberOfComponents();
            if (decode != null && decode.length != n * 2)
            {
                LOG.warn("Ignored invalid decode array: not compatible with color space");
                decode = null;
            }
        }

        // use color space default
        if (decode == null)
        {
            return pdImage.getColorSpace().getDefaultDecode(pdImage.getBitsPerComponent());
        }

        return decode;
    }
}
