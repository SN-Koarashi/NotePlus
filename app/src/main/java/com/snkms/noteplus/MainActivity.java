package com.snkms.noteplus;

import static com.snkms.noteplus.GlobalStatic.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
//import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
//import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.function.Function;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, View.OnLongClickListener {
    // 主畫面

    long timestamp = 0;
    ListView lvFilter, lvType;
    Button btList, btNote, btDate;
    ImageButton btDatePrevious, btDateNext;
    SQLiteDatabase db;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 將物件綁定到變數
        btDate = findViewById(R.id.btDate);
        btList = findViewById(R.id.btAddList);
        btNote = findViewById(R.id.btAddNote);
        btDateNext = findViewById(R.id.btDateNext);
        btDatePrevious = findViewById(R.id.btDatePrevious);
        lvFilter = findViewById(R.id.lvFilter);
        lvType = findViewById(R.id.lvType);


        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date date = new Date();
        String currentTime = sdf.format(date.getTime());

        timestamp = date.getTime();
        btDate.setText(currentTime);

        // 註冊事件
        btDate.setOnClickListener(this);
        btDate.setOnLongClickListener(this);

        btNote.setOnClickListener(this);
        btList.setOnClickListener(this);
        btDateNext.setOnClickListener(this);
        btDatePrevious.setOnClickListener(this);

        lvFilter.setOnItemClickListener(this);
        lvFilter.setOnItemLongClickListener(this);
        lvType.setOnItemClickListener(this);
        lvType.setOnItemLongClickListener(this);


        initDatabase();
    }

    // 初始化 SQLite 資料庫
    private void initDatabase(){
        db = openOrCreateDatabase(db_name, Context.MODE_PRIVATE, null);

        String createTableNote = "CREATE TABLE IF NOT EXISTS " + tb_table + " ( `_id` INTEGER PRIMARY KEY, `title` VARCHAR(64) NOT NULL , `content` MEDIUMTEXT NOT NULL , `type` VARCHAR(64) NOT NULL, `priority` TINYINT NOT NULL ,  `date` DATE NOT NULL , `doneDate` DATETIME NOT NULL , `isDone` TINYINT NOT NULL);";
        String createTableImageList = "CREATE TABLE IF NOT EXISTS " + tb_table_img + " ( `_id` INTEGER PRIMARY KEY, `note_id` INT NOT NULL , `image` BLOB NOT NULL);";
        String createTableTypeList = "CREATE TABLE IF NOT EXISTS " + tb_table_type + " ( `_id` INTEGER PRIMARY KEY, `name` VARCHAR(64) NOT NULL);";
        // 執行SQL語法
        db.execSQL(createTableNote);
        db.execSQL(createTableImageList);
        db.execSQL(createTableTypeList);

        Cursor c = db.rawQuery("SELECT _id FROM "+ tb_table_type +" WHERE name = ?", new String[]{undefined_type_name});;
        if(c.getCount() == 0){
            db.execSQL("INSERT INTO "+tb_table_type+"(name) VALUES(?)",new String[]{undefined_type_name});
        }
        c.close();

        Log.v("Database", "PATH: " + db.getPath());
        readFilterData();
        readTypeData();
    }

    // 取得按日期過濾的資料
    private void readFilterData(){
        String[] FROM = new String[] {"_id","title","content","type","priority"};

        Cursor c = db.rawQuery("SELECT _id,title,content,type,priority FROM "+ tb_table+ " WHERE date = ? AND isDone = 0 ORDER BY priority DESC, _id DESC",new String[]{
                btDate.getText().toString()
        });
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                R.layout.item_filter, c,
                FROM,
                new int[]{R.id.ifId, R.id.ifTitle, R.id.ifContent, R.id.ifType,R.id.ifImportant}, 0);
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View aView, Cursor aCursor, int aColumnIndex) {
                // 如果查詢索引是3 (分類欄位)時，將內容格式化為 #分類名稱
                if (aColumnIndex == 3) {
                    String data = aCursor.getString(aColumnIndex);
                    TextView textView = (TextView) aView;
                    textView.setText("#" + data);
                    return true;
                }

                // 如果查詢索引是4 (是否重要欄位)時
                if (aColumnIndex == 4) {
                    String data = aCursor.getString(aColumnIndex);

                    // 如果不是重要記事，不顯示 TextView
                    if(Integer.parseInt(data) == 0){
                        TextView textView = (TextView) aView;
                        textView.setText("");
                        textView.setVisibility(View.GONE);
                    }
                    else{
                        TextView textView = (TextView) aView;
                        textView.setText("[重要]");
                        textView.setVisibility(View.VISIBLE);
                    }
                    return true;
                }

                return false;
            }
        });

        lvFilter.setAdapter(adapter);
    }

    // 取得按分類過濾的資料
    private void readTypeData(){
        String[] FROM = new String[] {"type","COUNT"};

        Cursor c = db.rawQuery("SELECT a._id,a.name AS type, COUNT(b._id) AS COUNT FROM "+tb_table_type+" AS a LEFT JOIN "+tb_table+" AS b ON a.name=b.type GROUP by a.name ORDER BY a._id ASC",null);

        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                R.layout.item_type, c,
                FROM,
                new int[]{R.id.itTitle,R.id.itNumber}, 0);

        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {

            public boolean setViewValue(View aView, Cursor aCursor, int aColumnIndex) {

                // 如果查詢索引是2 (數量欄位)時，將內容格式化為 (數量)
                if (aColumnIndex == 2) {
                    String data = aCursor.getString(aColumnIndex);
                    TextView textView = (TextView) aView;
                    textView.setText("(" + data + ")");
                    return true;
                }

                return false;
            }
        });
        lvType.setAdapter(adapter);
    }


    // 接收 編輯記事或是新增記事 的回呼(callback)函數
    @Override
    protected void onActivityResult(int request, int result, Intent it) {
        super.onActivityResult(request, result, it);
        if (result == RESULT_OK) {
            readFilterData();
            readTypeData();
        }
    }

    // 在 ActionBar 新增功能按鈕
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, Menu.FIRST, Menu.NONE, "所有記事");
        menu.add(Menu.NONE, Menu.FIRST + 1, Menu.NONE, "重新整理");
        return super.onCreateOptionsMenu(menu);
    }

    // 在 ActionBar 中接收事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent it;
        switch (item.getItemId()) {
            case Menu.FIRST:
                // 按下 "查看所有記事" 時
                it = new Intent(this, NoteActivity.class);
                startActivity(it);
                break;
            case Menu.FIRST + 1:
                // 按下 "重新整理" 時
                readFilterData();
                readTypeData();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    // 離開應用程式
    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("關閉程式")
                .setMessage("確定離開?")
                .setPositiveButton("是", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }

                })
                .setNegativeButton("否", null)
                .show();
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

        readFilterData();
    }

    // 新增或編輯記事方法
    private void onAddNote(int id){
        // 新增記事
        Intent it = new Intent(MainActivity.this, ActionNoteActivity.class);
        // -1 表示新增記事，大於0的數字表示記事ID
        it.putExtra("action", id);
        if(id == -1)
            it.putExtra("timestamp",timestamp);

        startActivityForResult(it, 100);
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
                calendar.set(Calendar.MONTH, i1);
                calendar.set(Calendar.DAY_OF_MONTH, i2);

                String myFormat = "yyyy-MM-dd";
                SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.TAIWAN);
                btDate.setText(sdf.format(calendar.getTime()));
                timestamp = calendar.getTime().getTime();
                readFilterData();
            }
        };

        DatePickerDialog dialog = new DatePickerDialog(MainActivity.this,
                datePicker,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    // 新增分類名稱輸入框
    private void onAddList(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新增分類名稱");

        final EditText input = new EditText(this);
        final int dp = dpToPx(5,MainActivity.this.getResources());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("新增", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 如果輸入的內容，不包含空白字元時為 0 時
                if(input.getText().toString().trim().length() == 0){
                    Toast.makeText(MainActivity.this, "請輸入內容", Toast.LENGTH_SHORT).show();
                }
                // 如果輸入的內容，不包含空白字元時為 >20 時
                else if(input.getText().toString().trim().length() > 20){
                    Toast.makeText(MainActivity.this, "分類名稱不能超過20字", Toast.LENGTH_SHORT).show();
                }
                else {
                    Cursor c = db.rawQuery("SELECT * FROM " + tb_table_type + " WHERE name = ?", new String[]{
                            input.getText().toString()
                    });

                    // 如果以分類為條件，有查詢到資料時，表示分類已經建立
                    if (c.getCount() > 0) {
                        Toast.makeText(MainActivity.this, "該分類名稱已存在", Toast.LENGTH_SHORT).show();
                    } else {
                        db.execSQL("INSERT INTO " + tb_table_type + "(name) VALUES(?)",
                                new Object[]{
                                        input.getText().toString()
                                });

                        Toast.makeText(MainActivity.this, "新增完成", Toast.LENGTH_SHORT).show();
                        readTypeData();
                    }

                    c.close();
                }
            }
        });

        builder.show();
    }

    // 回傳對話框建構子
    private AlertDialog.Builder getChooseDialog(MainActivity v, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(v);
        builder.setTitle(title);
        AlertDialog alert = builder.create();
        alert.setCanceledOnTouchOutside(false);

        return builder;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btAddNote:
                onAddNote(-1);break;
            case R.id.btDate:
                onPickDate();break;
            case R.id.btDatePrevious:
                setDateTime(false);break;
            case R.id.btDateNext:
                setDateTime(true);break;
            case R.id.btAddList:
                onAddList();break;
        }
    }

    // LISTVIEW 被點選時
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // 如果是點選首頁記事列表的 listview
        if(parent.getId() == R.id.lvFilter) {
            TextView c = parent.getChildAt(position - parent.getFirstVisiblePosition()).findViewById(R.id.ifId);
            String _id = c.getText().toString();

            onAddNote(Integer.parseInt(_id));
        }

        // 如果是點選首頁分類列表的 listview
        if(parent.getId() == R.id.lvType) {
            TextView c = parent.getChildAt(position - parent.getFirstVisiblePosition()).findViewById(R.id.itTitle);
            String type = c.getText().toString();

            Intent it = new Intent(this, NoteActivity.class);
            it.putExtra("type", type);
            startActivityForResult(it,100);
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
        // 如果是長按首頁記事列表的 listview
        if(adapterView.getId() == R.id.lvFilter) {
            TextView c = adapterView.getChildAt(i - adapterView.getFirstVisiblePosition()).findViewById(R.id.ifId);
            String _id = c.getText().toString();

            new AlertDialog.Builder(this)
                    .setTitle("刪除記事")
                    .setMessage("確定要刪除這個記事嗎?")
                    .setPositiveButton("是", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 刪除記事
                            db.execSQL("DELETE FROM " + tb_table + " WHERE _id = ?", new String[]{
                                    String.valueOf(_id)
                            });

                            // 刪除與記事關聯的圖片
                            db.execSQL("DELETE FROM " + tb_table_img + " WHERE note_id = ?", new String[]{
                                    String.valueOf(_id)
                            });

                            readFilterData();
                            readTypeData();
                        }

                    })
                    .setNegativeButton("否", null)
                    .show();
        }

        // 如果是長按首頁分類列表的 listview
        if(adapterView.getId() == R.id.lvType){
            TextView c = adapterView.getChildAt(i - adapterView.getFirstVisiblePosition()).findViewById(R.id.itTitle);
            String name = c.getText().toString();

            // 如果要刪除未分類這個預設分類時，顯示不能刪除
            if(name.equals(undefined_type_name)){
                Toast.makeText(this, "該分類無法刪除", Toast.LENGTH_SHORT).show();
            }
            else {
                new AlertDialog.Builder(this)
                        .setTitle("刪除分類")
                        .setMessage("確定要刪除分類[" + name + "]嗎?")
                        .setPositiveButton("是", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 刪除分類
                                db.execSQL("DELETE FROM " + tb_table_type + " WHERE name = ?", new String[]{name});

                                // 將該分類所在的記事改為未分類
                                db.execSQL("UPDATE " + tb_table + " SET type = ? WHERE type = ?", new String[]{undefined_type_name, name});

                                readFilterData();
                                readTypeData();
                            }

                        })
                        .setNegativeButton("否", null)
                        .show();
            }
        }
        return true;
    }

    @Override
    public boolean onLongClick(View v) {
        // 長按日期按鈕時，回到當天日期
        if(v.getId() == R.id.btDate){
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = new Date();
            String currentTime = sdf.format(date.getTime());

            timestamp = date.getTime();
            if(!btDate.getText().toString().equals(currentTime))
                Toast.makeText(this, "回到今天囉~", Toast.LENGTH_SHORT).show();

            btDate.setText(currentTime);

            readFilterData();
        }
        return true;
    }
}