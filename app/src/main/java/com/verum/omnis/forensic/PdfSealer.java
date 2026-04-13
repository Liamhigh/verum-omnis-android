
package com.verum.omnis.forensic;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.File;

public interface PdfSealer {
    class Result { public File pdfFile; public String sha512Hex; }
    Result seal(Context ctx, File inputFile, Bitmap logo) throws Exception;
}
