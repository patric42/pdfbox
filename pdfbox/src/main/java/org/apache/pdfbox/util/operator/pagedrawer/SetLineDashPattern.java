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
package org.apache.pdfbox.util.operator.pagedrawer;

import java.util.List;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdfviewer.PageDrawer;
import org.apache.pdfbox.pdmodel.graphics.PDLineDashPattern;
import org.apache.pdfbox.util.PDFOperator;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.io.IOException;

/**
 * Implementation of content stream operator for page drawer.
 *
 * @author <a href="mailto:andreas@lehmi.de">Andreas Lehmkühler</a>
 * @version $Revision: 1.0 $
 */
public class SetLineDashPattern extends org.apache.pdfbox.util.operator.SetLineDashPattern
{

    /**
     * Set the line dash pattern.
     * @param operator The operator that is being executed.
     * @param arguments List
     *
     * @throws IOException If an error occurs while processing the font.
     */
    public void process(PDFOperator operator, List<COSBase> arguments) throws IOException
    {
        super.process( operator, arguments );
        PDLineDashPattern lineDashPattern = context.getGraphicsState().getLineDashPattern();
        PageDrawer drawer = (PageDrawer)context;
        BasicStroke stroke = (BasicStroke)drawer.getStroke();
        if (stroke == null) 
        {
            if (lineDashPattern.isDashPatternEmpty()) 
            {
                drawer.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f) );
            }
            else
            {
                drawer.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f,
                        lineDashPattern.getCOSDashPattern().toFloatArray(), lineDashPattern.getPhaseStart()) );
            }
        }
        else 
        {
            if (lineDashPattern.isDashPatternEmpty()) 
            {
                drawer.setStroke( new BasicStroke(stroke.getLineWidth(), stroke.getEndCap(), 
                        stroke.getLineJoin(), stroke.getMiterLimit()) );
            }
            else
            {
                drawer.setStroke( new BasicStroke(stroke.getLineWidth(), stroke.getEndCap(), stroke.getLineJoin(), 
                        stroke.getMiterLimit(), lineDashPattern.getCOSDashPattern().toFloatArray(), 
                        lineDashPattern.getPhaseStart()) );
            }
        }
    }
    
}
