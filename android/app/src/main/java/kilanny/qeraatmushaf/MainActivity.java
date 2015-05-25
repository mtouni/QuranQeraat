package kilanny.qeraatmushaf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class MainActivity extends Activity {

    private final String settingFilename = "myfile";
    private boolean isActionbarVisible = false;
    private Setting setting;
    private static final int MAX_PAGE = 604;
    private boolean isLoadingPage;
    private final Surah[] values = new Surah[114];
    private final int pageSizes[][] = new int[MAX_PAGE][2];
    private final ArrayList<Selection>[] sel = new ArrayList[MAX_PAGE];

    private void readSettings() {
        try {
            FileInputStream fis = openFileInput(settingFilename);
            ObjectInputStream is = new ObjectInputStream(fis);
            setting = (Setting) is.readObject();
            is.close();
            fis.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        if (setting == null) {
            setting = new Setting();
        }
    }

    private void saveSettings() {
        try {
            FileOutputStream fos = openFileOutput(settingFilename, Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(setting);
            os.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteOldImage(QuranImageView v) {
        Drawable drawable = v.getDrawable();
        if (v.selections != null && drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            Bitmap bitmap = bitmapDrawable.getBitmap();
            bitmap.recycle();
        }
    }

    private void viewPage(final int p) {
        if (p > 0 && p <= MAX_PAGE && !isLoadingPage) {
            isLoadingPage = true;
            final QuranImageView v = (QuranImageView) findViewById(R.id.imageView);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    deleteOldImage(v);
                    v.selections = null;
                    v.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ajax_loader));
                    v.invalidate();
                    setProgressBarIndeterminateVisibility(true);
                }
            });
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String path = String.format(getString(R.string.downloadPage), p);
                    Bitmap bb = readPage(p);
                    if (bb == null) {
                        bb = getBitmapFromURL(path);
                        if (bb != null && setting.autoSaveDownloadedPage) {
                            writePage(p, bb);
                        }
                    }
                    final Bitmap b = bb;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (b == null) {
                                showAlert("خطأ", "لا يمكن تحميل الصورة. تأكد من اتصالك بالانترنت");
                            } else {
                                v.currentPageSize = new Dimension(pageSizes[p - 1][0],
                                        pageSizes[p - 1][1]);
                                v.setImageBitmap(b);
                                v.selections = sel[p - 1];
                                CharSequence text = "صفحة " + p;
                                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                                setting.page = p;
                                saveSettings();
                            }
                            setProgressBarIndeterminateVisibility(false);
                            isActionbarVisible = false;
                            getActionBar().hide();
                            isLoadingPage = false;
                        }
                    });
                }
            }).start();
        }
    }

    public static String getString(SelectionType s) {
        switch (s) {
            case Edgham:
                return ("النوع: إدغام");
            case Emalah:
                return ("النوع: إمالة");
            case Farsh:
                return ("النوع: فرش");
            case Hamz:
                return ("النوع: همز");
            case Mad:
                return ("النوع: مد");
            case Naql:
                return ("النوع: نقل");
            case Sakt:
                return ("النوع: سكت");
        }
        throw new IllegalArgumentException();
    }

    private void displaySelection(Selection s) {
        final Dialog dialog = new Dialog(this);
        dialog.setTitle(R.string.app_name);
        dialog.setContentView(R.layout.view_qeraat_dlg);
        TextView txt = (TextView) dialog.findViewById(R.id.textViewType);
        txt.setText(getString(s.type));
        txt = (TextView) dialog.findViewById(R.id.textViewShahed);
        txt.setText(s.shahed);
        txt = (TextView) dialog.findViewById(R.id.textViewDescr);
        txt.setText(s.descr);
        dialog.show();
    }

    public static Bitmap getBitmapFromURL(String link) {
        System.out.println(link);
        try {
            URL url = new URL(link);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            BitmapFactory.Options o = new BitmapFactory.Options();
            return BitmapFactory.decodeStream(input, new Rect(0, 0, 0, 0), o);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void readSelections() {
        for (int i = 0; i < MAX_PAGE; ++i) {
            sel[i] = new ArrayList<>();
        }
        XmlResourceParser parser = getResources().getXml(R.xml.data);
        QuranImageView v = (QuranImageView) findViewById(R.id.imageView);
        try {
            int eventType = parser.getEventType();
            Selection currentProduct = null;
            while (eventType != XmlPullParser.END_DOCUMENT){
                String name = null;
                switch (eventType){
                    case XmlPullParser.START_TAG:
                        name = parser.getName();
                        if (name.equals("selection")) {
                            currentProduct = new Selection();
                            currentProduct.page = parser.getAttributeIntValue(null, "page", 1);
                            String rect = parser.getAttributeValue(null, "rect");
                            if (rect != null) {
                                Rectangle r = new Rectangle();
                                String[] tmp = rect.split(",");
                                r.x = Float.parseFloat(tmp[0]);
                                r.y = Float.parseFloat(tmp[1]);
                                r.width = Float.parseFloat(tmp[2]);
                                r.height = Float.parseFloat(tmp[3]);
                                currentProduct.rect = r;
                            } else {
                                rect = parser.getAttributeValue(null, "line");
                                Line r = new Line();
                                String[] tmp = rect.split(",");
                                r.x1 = Float.parseFloat(tmp[0]);
                                r.y1 = Float.parseFloat(tmp[1]);
                                r.x2 = Float.parseFloat(tmp[2]);
                                r.y2 = Float.parseFloat(tmp[3]);
                                currentProduct.rect = r;
                            }
                            currentProduct.type = SelectionType.fromValue(
                                    parser.getAttributeIntValue(null, "type", 1));
                        } else if (currentProduct != null){
                            if (name.equals("descr")){
                                currentProduct.descr = parser.nextText();
                            } else if (name.equals("shahed")){
                                currentProduct.shahed = parser.nextText();
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        name = parser.getName();
                        if (name.equalsIgnoreCase("selection") && currentProduct != null){
                            sel[currentProduct.page - 1].add(currentProduct);
                        }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }
    }

    private Surah getSurah(int idx) {
        XmlResourceParser parser = getResources().getXml(R.xml.qurandata);
        Surah s = new Surah();
        s.index = idx;
        s.page = MAX_PAGE * 2;
        try {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT){
                String name = null;
                switch (eventType){
                    case XmlPullParser.START_TAG:
                        name = parser.getName();
                        if (name.equals("sura") &&
                                parser.getAttributeIntValue(null, "index", 0) == idx){
                            s.name = parser.getAttributeValue(null, "name");
                        } else if (name.equals("page") &&
                                parser.getAttributeIntValue(null, "sura", 0) == idx){
                            s.page = Math.min(s.page, parser.getAttributeIntValue(null, "index", 0));
                        } else if (name.equals("pagedim")) {
                            int indx = parser.getAttributeIntValue(null, "index", 0) - 1;
                            if (pageSizes[indx][0] == 0) {
                                pageSizes[indx][0] = parser.getAttributeIntValue(null, "width", 0);
                                pageSizes[indx][1] = parser.getAttributeIntValue(null, "height", 0);
                            }
                        }
                        break;
                }
                eventType = parser.next();
            }
            return s;
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void displayMulipleChoiceSelections(Selection[] s) {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(this);
        builderSingle.setTitle("عدة ملاحظات:");
        final ArrayAdapter<Selection> arrayAdapter = new ArrayAdapter<Selection>(
                this, android.R.layout.select_dialog_singlechoice, s);
        builderSingle.setAdapter(arrayAdapter,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        displaySelection(arrayAdapter.getItem(which));
                    }
                });
        builderSingle.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView v = (ImageView) findViewById(R.id.imageView);
        v.setOnTouchListener(new OnSwipeTouchListener(this) {
            private ArrayList<Selection> matches = new ArrayList<Selection>();

            @Override
            public void onSwipeLeft() {
                super.onSwipeLeft();
                viewPage(setting.page - 1);
            }

            @Override
            public void onSwipeRight() {
                super.onSwipeRight();
                viewPage(setting.page + 1);
            }

            @Override
            public void onClick(final float x, final float y) {
                super.onClick(x, y);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        QuranImageView i = (QuranImageView) findViewById(R.id.imageView);
                        matches.clear();
                        i.getSelectionAtPos(x, y, matches);
                        if (matches.size() > 0) {
                            if (matches.size() == 1)
                                displaySelection(matches.get(0));
                            else
                                displayMulipleChoiceSelections((Selection[]) matches.toArray());
                        } else if (isActionbarVisible)
                            getActionBar().hide();
                        else getActionBar().show();
                        isActionbarVisible = !isActionbarVisible;
                    }
                });
            }
        });
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                isLoadingPage = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        QuranImageView v = (QuranImageView) findViewById(R.id.imageView);
                        v.selections = null;
                        v.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ajax_loader));
                        v.invalidate();
                    }
                });
                for (int i = 0; i < 114; ++i) {
                    values[i] = getSurah(i + 1);
                }
                readSelections();
                isLoadingPage = false;
                readSettings();
                viewPage(setting.page);
                return null;
            }
        }.execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (isLoadingPage) {
            ; // ignore any op until finish loading
        } else if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_goto) {
            final Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.fragment_goto_dlg);
            EditText txt = (EditText) dialog.findViewById(R.id.editTextPageNum);
            txt.setText("" + setting.page);
            dialog.setTitle("ذهاب إلى الصفحة");
            final ListView l = (ListView) dialog.findViewById(R.id.listViewSurah);
            l.setAdapter(new ArrayAdapter<Surah>(this,
                    android.R.layout.simple_list_item_1, android.R.id.text1, values));
            l.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Surah itemValue = (Surah) l.getItemAtPosition(position);
                    EditText txt = (EditText) dialog.findViewById(R.id.editTextPageNum);
                    txt.setText("" + itemValue.page);
                }
            });
            Button b = (Button) dialog.findViewById(R.id.buttonGoto);
            b.setOnClickListener(new View.OnClickListener() {
                private Runnable err = new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(MainActivity.this);
                        dlgAlert.setMessage(String.format("أدخل رقم صفحة صحيح في المدى (1-%d)", MAX_PAGE));
                        dlgAlert.setTitle("خطأ");
                        dlgAlert.setPositiveButton("موافق", null);
                        dlgAlert.setCancelable(false);
                        dlgAlert.create().show();
                    }
                };

                @Override
                public void onClick(View v) {
                    try {
                        EditText txt = (EditText) dialog.findViewById(R.id.editTextPageNum);
                        int num = Integer.parseInt(txt.getText().toString());
                        if (num > 0 && num <= MAX_PAGE) {
                            dialog.dismiss();
                            viewPage(num);
                        } else {
                            err.run();
                        }
                    } catch (Exception ex) {
                        err.run();
                    }
                }
            });
            dialog.show();
            return true;
        } else if (R.id.action_download == id) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    switch (which){
                        case DialogInterface.BUTTON_POSITIVE:
                            downloadAll();
                            break;
                        case DialogInterface.BUTTON_NEGATIVE:
                            //No button clicked
                            break;
                    }
                }
            };
            builder.setMessage("سيتم تحميل المصحف كاملا على جهازك (150 ميغا) استمرار؟")
                    .setPositiveButton("نعم", dialogClickListener)
                    .setNegativeButton("لا", dialogClickListener).show();
        }

        return super.onOptionsItemSelected(item);
    }

    private void showAlert(String title, String msg) {
        AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(this);
        dlgAlert.setMessage(msg);
        dlgAlert.setTitle(title);
        dlgAlert.setPositiveButton("موافق", null);
        dlgAlert.setCancelable(false);
        dlgAlert.create().show();
    }

    private void downloadAll() {
        final ProgressDialog show = ProgressDialog.show(this, "تحميل المصحف كاملا",
                "يتم تحميل المصحف...", true, true);
        final Handler updateBarHandler = new Handler();
        show.setMax(MAX_PAGE);
        show.setProgress(0);
        show.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        final AsyncTask<Void, Integer, String[]> execute = new AsyncTask<Void, Integer, String[]>() {
            @Override
            protected String[] doInBackground(Void... params) {
                for (int p = 1; !isCancelled() && p <= MAX_PAGE; ++p) {
                    final int per = p;
                    publishProgress(per);
                    String path = String.format(getString(R.string.downloadPage), p);
                    Bitmap bb = readPage(p);
                    if (bb == null) {
                        Bitmap b = getBitmapFromURL(path);
                        if (b == null) {
                            return new String[] {"خطأ", "فشلت عملية التحميل. تأكد من اتصالك بالانترنت"};
                        } else {
                            if (!writePage(p, b)) {
                                return new String[] {"خطأ", "لا يمكن كتابة الملف. تأكد من وجود مساحة كافية"};
                            }
                        }
                    } else {
                        bb.recycle();
                    }
                }
                if (!isCancelled()) {
                    return new String[]{"تحميل المصحف", "جميع الصفحات تم تحميلها بنجاح"};
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(final Integer... values) {
                updateBarHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        show.setProgress(values[0]);
                        show.setMessage(String.format("يتم تحميل الصفحة %d من %d",
                                values[0], MAX_PAGE));
                    }
                });
            }

            @Override
            protected void onCancelled() {
                //super.onCancelled();
                show.dismiss();
            }

            @Override
            protected void onPostExecute(String[] strings) {
                //super.onPostExecute(strings);
                show.dismiss();
                showAlert(strings[0], strings[1]);
            }
        }.execute();
        show.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                execute.cancel(true);
            }
        });
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public File getAlbumStorageDir(Context context) {
        // Get the directory for the app's private pictures directory.
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "quran_Images");
        if (!file.exists() && !file.mkdirs()) {
            Log.e("QuranQeraat", "Directory not created");
        }
        return file;
    }

    private boolean pageExists(int idx) {
        File filename = new File(getAlbumStorageDir(getApplicationContext()), idx + "");
        return filename.exists();
    }

    private Bitmap readPage(int idx) {
        try {
            File filename = new File(getAlbumStorageDir(getApplicationContext()), idx + "");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(filename.getPath(), options);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private boolean writePage(int idx, Bitmap b) {
        FileOutputStream out = null;
        File filename = new File(getAlbumStorageDir(getApplicationContext()), idx + "");
        try {
            out = new FileOutputStream(filename);
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            // PNG is a lossless format, the compression factor (100) is ignored
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                    return true;
                }
                return false;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}

enum SelectionType {
    Farsh(1),
    Hamz(2),
    Edgham(3),
    Emalah(4),
    Naql(5),
    Mad(6),
    Sakt(7);

    private final int value;
    private SelectionType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static SelectionType fromValue(int t) {
        switch (t) {
            case 1:
                return Farsh;
            case 2:
                return Hamz;
            case 3:
                return Edgham;
            case 4:
                return Emalah;
            case 5:
                return Naql;
            case 6:
                return Mad;
            case 7:
                return Sakt;
            default:
                throw new IllegalArgumentException();
        }
    }
}

abstract class Shape {

}

class Rectangle extends Shape {
    float x, y, width, height;

    @Override
    public String toString() {
        return String.format("rect[%f, %f, %f, %f]", x, y, x+width, y+height);
    }
}

class Line extends Shape {
    float x1, y1, x2, y2;

    @Override
    public String toString() {
        return String.format("rect[%f, %f, %f, %f]", x1, y1, x2, y2);
    }
}

class Selection {
    Shape rect;
    int page;
    String shahed;
    String descr;
    SelectionType type;

    @Override
    public String toString() {
        return MainActivity.getString(type);
    }
}
class Surah {
    String name;
    int page;
    int index;

    @Override
    public String toString() {
        return "سورة " + name;
    }
}

class Setting implements Serializable {
    boolean autoSaveDownloadedPage = true;
    int page = 1;
}

class SynchObj<T> {
    private T data;
    private Lock lock = new ReentrantLock(true);

    public T getData() {
        try {
            lock.lock();
            return data;
        } finally {
            lock.unlock();
        }
    }

    public void setData(T data) {
        try {
            lock.lock();
            this.data = data;
        } finally {
            lock.unlock();
        }
    }
}