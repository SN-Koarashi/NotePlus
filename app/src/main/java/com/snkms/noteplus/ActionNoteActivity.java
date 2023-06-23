package com.snkms.noteplus;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Notification;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.renderscript.ScriptGroup;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.snkms.noteplus.GlobalStatic.*;

public class ActionNoteActivity extends AppCompatActivity implements View.OnClickListener, View.OnLongClickListener {
    // 新增/查看 記事畫面

    long timestamp = 0;
    Uri imgUri;    //用來參照拍照存檔的 Uri 物件
    ArrayList<Bitmap> bitmapArray = new ArrayList<>(); // 儲存照片的BITMAP
    Button btDate;
    ImageButton btSave, btInsertImage, btDatePrevious, btDateNext;
    EditText edTitle, edContent;
    Spinner spType;
    Switch chkPriority, chkDone;
    HorizontalScrollView vwGallery;
    int noteID = 0;
    boolean keyboardOpenState = false;
    SQLiteDatabase db;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_action_note);

        // 取得意圖內容
        Intent it = this.getIntent();
        timestamp = it.getLongExtra("timestamp",0);
        noteID = it.getIntExtra("action",-1);

        // 將物件綁定到變數
        btDate = findViewById(R.id.btActionDate);
        btSave = findViewById(R.id.btSave);
        btInsertImage = findViewById(R.id.btInsertImage);
        btDateNext = findViewById(R.id.btActionDateNext);
        btDatePrevious = findViewById(R.id.btActionDatePrevious);

        edTitle = findViewById(R.id.edTitle);
        edContent = findViewById(R.id.edContent);
        spType = findViewById(R.id.spType);
        chkPriority = findViewById(R.id.chkPriority);
        chkDone = findViewById(R.id.chkDone);

        vwGallery = findViewById(R.id.vwGallery);


        // 註冊事件
        btSave.setOnClickListener(this);
        btDateNext.setOnClickListener(this);
        btDatePrevious.setOnClickListener(this);
        btInsertImage.setOnClickListener(this);
        btDate.setOnClickListener(this);
        btDate.setOnLongClickListener(this);


        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date date = new Date();

        if(timestamp > 0)
            date.setTime(timestamp);

        String currentTime = sdf.format(date.getTime());
        btDate.setText(currentTime);
        timestamp = date.getTime();

        db = openOrCreateDatabase(db_name, Context.MODE_PRIVATE, null);

        // -1 表示新增記事，大於0的數字表示記事ID
        if(noteID == -1){
            this.getSupportActionBar().setTitle("新增記事");
            setTypeData(this.getIntent().getStringExtra("type"));
        }
        else{
            this.getSupportActionBar().setTitle("編輯記事");
            ApplyData();
        }

        this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        // https://stackoverflow.com/questions/2150078/how-to-check-visibility-of-software-keyboard-in-android
        // 偵測虛擬鍵盤是否處於開啟狀態的事件
        View rootView = findViewById(R.id.rootLayout);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int heightDiff = rootView.getRootView().getHeight() - rootView.getHeight();

                // 開啟鍵盤狀態
                if (heightDiff > dpToPx(200, ActionNoteActivity.this.getResources())) { // if more than 200 dp, it's probably a keyboard...
                    keyboardOpenState = true;
                    vwGallery.setVisibility(View.GONE);
                }
                else if(bitmapArray.size() > 0){
                    keyboardOpenState = false;
                    vwGallery.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btSave:
                onSaveData();break;
            case R.id.btActionDate:
                onPickDate();break;
            case R.id.btInsertImage:
                onInsertImage();break;
            case R.id.btActionDateNext:
                setDateTime(true);break;
            case R.id.btActionDatePrevious:
                setDateTime(false);break;
        }
    }
    @Override
    public boolean onLongClick(View v) {
        // 長按日期按鈕時回到當天日期
        if(v.getId() == R.id.btActionDate){
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = new Date();
            String currentTime = sdf.format(date.getTime());

            timestamp = date.getTime();
            if(!btDate.getText().toString().equals(currentTime))
                Toast.makeText(this, "回到今天囉~", Toast.LENGTH_SHORT).show();

            btDate.setText(currentTime);
        }
        return true;
    }

    // 設定日期
    private void setDateTime(boolean hasNext){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date dt = new Date();

        if(timestamp > 0)
            dt.setTime(timestamp);

        Calendar c = Calendar.getInstance();
        c.setTime(dt);

        c.add(Calendar.DATE, (hasNext)?1:-1);

        dt = c.getTime();

        timestamp = dt.getTime();
        String currentTime = sdf.format(dt);
        btDate.setText(currentTime);
    }

    // 設定分類 SPINNER 選取的項目
    @SuppressLint("Range")
    private void setTypeData(String type){
        // 查詢分類資料表中的所有分類
        Cursor cType = db.rawQuery("SELECT * FROM "+tb_table_type,null);
        ArrayList<String> list = new ArrayList<>();

        // 如果有資料的話，將所有分類新增到下拉式選單(SPINNER)中供選擇
        if(cType.getCount() > 0){
            cType.moveToFirst();
            do{
                list.add(cType.getString(cType.getColumnIndex("name")));
            }while (cType.moveToNext());

            cType.close();
        }

        // 將資料放入下拉式選單中
        String[] dataArray = list.toArray(new String[0]);
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, dataArray);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(dataAdapter);

        // 如果是從分類記事類表 (NoteActivity 中有傳送意圖 type 分類) 中開啟這個 activity 的話，將分類自動選取為該分類
        if(type != null)
            spType.setSelection(dataAdapter.getPosition(type));
    }

    // 從 SQLite 套用資料到前端介面
    @SuppressLint("Range")
    private void ApplyData(){
        // 取得資料庫資料，並套用到所有前端物件
        Cursor c = db.rawQuery("SELECT * FROM " + tb_table + " WHERE _id = ?",new String[]{String.valueOf(noteID)});
        if(c.getCount() > 0){
            c.moveToFirst();

            edTitle.setText(c.getString(c.getColumnIndex("title")));
            edContent.setText(c.getString(c.getColumnIndex("content")));
            btDate.setText(c.getString(c.getColumnIndex("date")));

            chkPriority.setChecked(c.getInt(c.getColumnIndex("priority")) > 0);
            chkDone.setChecked(c.getInt(c.getColumnIndex("isDone")) > 0);

            setTypeData(c.getString(c.getColumnIndex("type")));


            Cursor cImg = db.rawQuery("SELECT * FROM " + tb_table_img + " WHERE note_id = ?",new String[]{String.valueOf(noteID)});
            if(cImg.getCount() > 0) {
                cImg.moveToFirst();
                do {
                    // 將 byte[] 轉換成 Bitmap
                    Bitmap bitmap = BitmapFactory.decodeByteArray(cImg.getBlob(cImg.getColumnIndex("image"))
                            , 0
                            , cImg.getBlob(cImg.getColumnIndex("image")).length
                    );
                    bitmapArray.add(bitmap);
                } while (cImg.moveToNext());

                onUpdateImageGallery();
            }
            cImg.close();
        }
        else {
            Toast.makeText(this, "無法取得記事資料", Toast.LENGTH_SHORT).show();
            finish();
        }
        c.close();
    }
    // 選擇日期視窗
    private void onPickDate(){
        Calendar calendar;
        calendar = Calendar.getInstance();

        if(timestamp > 0) {
            Date date = new Date();
            date.setTime(timestamp);
            calendar.setTime(date);
        }

        DatePickerDialog.OnDateSetListener datePicker = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker datePicker, int i, int i1, int i2) {
                calendar.set(Calendar.YEAR, i);
                calendar.set(Calendar.MONTH , i1);
                calendar.set(Calendar.DAY_OF_MONTH, i2);
                String myFormat = "yyyy-MM-dd";
                SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.TAIWAN);
                btDate.setText(sdf.format(calendar.getTime()));
            }
        };

        DatePickerDialog dialog = new DatePickerDialog(ActionNoteActivity.this,
                datePicker,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }
    // 將結果儲存到 SQLite
    private void onSaveData(){
        // 判斷標題及內容是否有填寫
        if(edTitle.getText().toString().trim().length() == 0 || edContent.getText().toString().trim().length() == 0){
            Toast.makeText(this, "標題或內容尚未填寫", Toast.LENGTH_SHORT).show();
            return;
        }

        // 判斷標題是否大於40字
        if(edTitle.getText().toString().length() > 40){
            Toast.makeText(this, "標題必須小於40字", Toast.LENGTH_SHORT).show();
            return;
        }

        // 新增狀態，ID為 -1 表示新增
        if(noteID == -1){
            // 新增主要資料
            db.execSQL("INSERT INTO "+tb_table+"(title,content,type,priority,date,doneDate,isDone) VALUES(?,?,?,?,?,?,?)",
                    new Object[]{
                            edTitle.getText().toString(),
                            edContent.getText().toString(),
                            spType.getSelectedItem().toString(),
                            chkPriority.isChecked() ? 1 : 0,
                            btDate.getText().toString(),
                            btDate.getText().toString(),
                            chkDone.isChecked() ? 1 : 0,
            });

            // 取得最新新增的資料
            Cursor c = db.rawQuery("SELECT _id FROM " + tb_table + " ORDER BY _id DESC LIMIT 1",null);
            if(c.getCount() != 0){
                c.moveToFirst();
                noteID = c.getInt(0);

                // 將圖片新增到關聯的資料表中
                for (Bitmap bit: bitmapArray) {
                    byte[] bArray = getBytesFromBitmap(bit);
                    db.execSQL("INSERT INTO "+tb_table_img+"(note_id,image) VALUES(?,?)",
                            new Object[]{
                                    noteID,
                                    bArray
                            });
                }
                Toast.makeText(this, "儲存完成", Toast.LENGTH_SHORT).show();
            }

            c.close();
        }
        else {
            // 如果有傳入ID意圖的資料(非-1的值)，表示要更新該則記事內容

            // 更新主要資料
            db.execSQL("UPDATE "+tb_table+" SET title=?,content=?,type=?,priority=?,date=?,doneDate=?,isDone=? WHERE _id = ?",
                    new Object[]{
                            edTitle.getText().toString(),
                            edContent.getText().toString(),
                            spType.getSelectedItem().toString(),
                            chkPriority.isChecked() ? 1 : 0,
                            btDate.getText().toString(),
                            btDate.getText().toString(),
                            chkDone.isChecked() ? 1 : 0,
                            noteID
                    });

            // 刪除所有與此記事關聯的圖片
            db.execSQL("DELETE FROM "+tb_table_img+" WHERE note_id = ?", new Object[]{noteID});

            // 將現有圖片新增到關聯的資料表中
            for (Bitmap bit: bitmapArray) {
                byte[] bArray = getBytesFromBitmap(bit);
                db.execSQL("INSERT INTO "+tb_table_img+"(note_id,image) VALUES(?,?)",
                        new Object[]{
                                noteID,
                                bArray
                        });
            }
            Toast.makeText(this, "修改完成", Toast.LENGTH_SHORT).show();
        }

        // 關閉 activity 並回傳結果
        Intent it = new Intent();
        it.putExtra("action",noteID);
        setResult(RESULT_OK,it);
        finish();
    }

    // 在 ActionBar 新增功能按鈕
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(noteID == -1) return false; // 如果是新增狀態，不添加按鈕

        menu.add(Menu.NONE, Menu.FIRST, Menu.NONE, "刪除記事");
        return super.onCreateOptionsMenu(menu);
    }

    // 在 ActionBar 中接收事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent it;
        switch (item.getItemId()) {
            case android.R.id.home:
                it = new Intent();
                it.putExtra("action",-1);
                setResult(RESULT_OK,it);
                finish();
                break;
            case Menu.FIRST:
                new AlertDialog.Builder(this)
                        .setTitle("刪除記事")
                        .setMessage("確定要刪除這個記事嗎?")
                        .setPositiveButton("是", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 刪除記事
                                db.execSQL("DELETE FROM " + tb_table + " WHERE _id = ?", new String[]{
                                        String.valueOf(noteID)
                                });

                                // 刪除與記事關聯的圖片
                                db.execSQL("DELETE FROM " + tb_table_img + " WHERE note_id = ?", new String[]{
                                        String.valueOf(noteID)
                                });
                                Intent it = new Intent();
                                it.putExtra("action",-1);
                                setResult(RESULT_OK,it);
                                finish();
                            }

                        })
                        .setNegativeButton("否", null)
                        .show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    // 將 bitmap 圖片轉換成 byte 格式，方便儲存進資料庫
    public static byte[] getBytesFromBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 65, stream);
            return stream.toByteArray();
        }
        return null;
    }

    // 更新畫面相片集
    private void onUpdateImageGallery(){
        // 移除頁面上的所有圖片
        LinearLayout imgGallery = findViewById(R.id.imgGallery);
        imgGallery.removeAllViewsInLayout();

        int index = 0;
        for (Bitmap bt:
             bitmapArray) {
            ImageView iv = new ImageView(this);

            // 為每個圖片物件新增點擊事件
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int tempID = Integer.parseInt(view.getTag().toString());
                    Intent it = new Intent(getApplicationContext(), ImageViewActivity.class);
                    it.putExtra("bitmap", getBytesFromBitmap(bitmapArray.get(tempID)));
                    startActivity(it);
                }
            });

            // 為每個圖片物件新增長按事件
            iv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    new AlertDialog.Builder(ActionNoteActivity.this)
                            .setTitle("刪除圖片")
                            .setMessage("確定要刪除張圖片嗎?")
                            .setPositiveButton("是", new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    int tempID = Integer.parseInt(view.getTag().toString());
                                    bitmapArray.remove(tempID);
                                    onUpdateImageGallery();
                                }

                            })
                            .setNegativeButton("否", null)
                            .show();
                    return true;
                }
            });

            // 設定圖片到物件中
            iv.setImageBitmap(bt);

            // 縮放圖片大小到 100dp
            RelativeLayout.LayoutParams params =
                    new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
            params.width = dpToPx(100,ActionNoteActivity.this.getResources());
            params.height = dpToPx(100,ActionNoteActivity.this.getResources());

            iv.setLayoutParams(params);

            // 設定圖片暫存ID
            iv.setTag(index);
            imgGallery.addView(iv);

            index++;
        }

        // 如果有圖片以及鍵盤非開啟狀態時
        if(bitmapArray.size() > 0 && !keyboardOpenState)
            vwGallery.setVisibility(View.VISIBLE);
        else
            vwGallery.setVisibility(View.GONE);
    }


    // 請求權限，參考自課本 Ch12
    private boolean storagePermission(){
        boolean STORAGE_PERM = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        boolean CAMERA_PERM = checkSelfPermission(android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        // 如果手機的API版本小於等於28，要針對儲存空間權限作特別處理
        if(Build.VERSION.SDK_INT <= 28 && !STORAGE_PERM || !CAMERA_PERM){
            ArrayList<String> permission = new ArrayList<>();
            permission.add(android.Manifest.permission.CAMERA);

            if(Build.VERSION.SDK_INT <= 28)
                permission.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);


            String[] stringArray = permission.toArray(new String[0]);
            requestPermissions(stringArray, 1);
            return false;
        }
        else{
            return true;
        }
    }

    // 請求權限回傳結果，參考自課本 Ch12
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            int grantedCount = 0;
            for (int result: grantResults) {
                if(result == PackageManager.PERMISSION_GRANTED)
                    grantedCount++;
            }

            if (grantResults.length > 0 && grantedCount == grantResults.length) {
                onInsertImage();
            }
            else{
                Toast.makeText(this, "授權失敗", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 插入圖片
    private void onInsertImage() {
        if(!storagePermission()) return;

        ActionNoteActivity vx = this;

        String[] option = new String[]{"檔案", "相機"};

        AlertDialog.Builder alert = getChooseDialog(this, "新增方式");
        alert.setSingleChoiceItems(option, 0, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case 0:
                        OpenImageGallery();
                        dialog.dismiss();
                        break;
                    case 1:
                        OpenImageCapture();
                        dialog.dismiss();
                        break;
                }
            }
        });

        alert.show();
    }

    // 回傳對話框建構子
    private AlertDialog.Builder getChooseDialog(ActionNoteActivity v, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(v);
        builder.setTitle(title);
        AlertDialog alert = builder.create();
        alert.setCanceledOnTouchOutside(false);

        return builder;
    }

    // 要求相機意圖，參考自課本 Ch10
    private void OpenImageCapture () {
        imgUri =  getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new ContentValues());
        Intent it = new Intent("android.media.action.IMAGE_CAPTURE");
        it.putExtra(MediaStore.EXTRA_OUTPUT, imgUri);    //將 uri 加到拍照 Intent 的額外資料中
        startActivityForResult(it, 100);
    }

    // 要求檔案總管意圖，參考自課本 Ch10
    public void OpenImageGallery() {
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);    //動作設為 "選取內容"
        it.setType("image/*");            //設定要選取的媒體類型為：所有類型的圖片
        startActivityForResult(it, 101);  //啟動意圖, 並要求傳回選取的圖檔
    }

    // 取得相片URI，參考自課本 Ch10
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(resultCode == Activity.RESULT_OK) {   //要求的意圖成功了
            switch(requestCode) {
                case 100: //拍照
                    Intent it = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, imgUri);//設為系統共享媒體檔
                    sendBroadcast(it);
                    break;
                case 101: //選取相片
                    imgUri = data.getData();  //取得選取相片的 Uri
                    break;
            }

            Bitmap bmp = null;
            try {
                bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(imgUri), null, null);

                // 如果 exif 有資料的話，讀取圖片的旋轉角度
                ExifInterface ei = new ExifInterface(getContentResolver().openInputStream(imgUri));
                int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED);
                int degress = 0;

                switch(orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        degress = 90;
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_180:
                        degress = 180;
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_270:
                        degress = 270;
                        break;
                }

                // 如果有旋轉角度就旋轉圖片
                if(degress > 0) {
                    Matrix matrix = new Matrix();  //建立 Matrix 物件
                    matrix.postRotate(degress);         //設定旋轉角度
                    bmp = Bitmap.createBitmap(bmp, //用原來的 Bitmap 產生一個新的 Bitmap
                            0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
                }

                // 當寬或高超過1500像素時，將圖片壓縮以及縮小為原來一半的尺寸
                if(bmp.getWidth() > 1500 || bmp.getHeight() > 1500){
                    bmp = Bitmap.createScaledBitmap(bmp, bmp.getWidth() / 2, bmp.getHeight() / 2, false);

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG,65,out);
                    bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0,
                            out.toByteArray().length);

                    out.close();
                }

                // 存入相片陣列
                bitmapArray.add(bmp);

                // 顯示相片
                onUpdateImageGallery();
            } catch (IOException e) {
                Toast.makeText(this, "無法取得照片", Toast.LENGTH_LONG).show();
            }
        }
        else {

            Toast.makeText(this, requestCode==100? "沒有拍到照片":
                            "沒有選取相片", Toast.LENGTH_LONG)
                    .show();
        }
    }


}