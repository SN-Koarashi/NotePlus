package com.snkms.noteplus;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import static com.snkms.noteplus.GlobalStatic.*;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class NoteActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    SQLiteDatabase db;
    ListView lvNote;
    String orderMethod = "priority DESC, date DESC, _id DESC";
    // 對話欄中的排序KEY
    int orderWhich = 0;
    int doneWhich = 0;
    HashMap<String, String> filterMethod = new HashMap<>();
    // 記事畫面
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);
        db = openOrCreateDatabase(db_name, Context.MODE_PRIVATE, null);

        this.getSupportActionBar().setHomeAsUpIndicator(android.R.drawable.ic_menu_close_clear_cancel);

        lvNote = findViewById(R.id.lvNote);
        lvNote.setOnItemClickListener(this);
        lvNote.setOnItemLongClickListener(this);

        String type = this.getIntent().getStringExtra("type");
        filterMethod.put("type",type);
        filterMethod.put("isDone","0");
        readFilterData(type,orderMethod);

        FloatingActionButton fab2 = findViewById(R.id.floatBtn2);
        fab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 按下右下角的 + 時
                // 新增記事
                Intent it = new Intent(NoteActivity.this, ActionNoteActivity.class);
                // -1 表示新增記事，大於0的數字表示記事ID
                it.putExtra("action", -1);

                // 如果是在分類中新增記事，則自動選擇該項分類
                it.putExtra("type", NoteActivity.this.getIntent().getStringExtra("type"));
                startActivityForResult(it, 100);
            }
        });

        setActionBarTitle();
    }

    // 設定 ActionBar 的標題
    private void setActionBarTitle(){
        String type = this.getIntent().getStringExtra("type");
        if(type == null){
            this.getSupportActionBar().setTitle("所有記事");
        }
        else{
            this.getSupportActionBar().setTitle("記事: "+ type);
        }
    }
    private void setActionBarTitle(String title){
        if(title != null)
            this.getSupportActionBar().setTitle(title);
        else
            setActionBarTitle();
    }

    // 取得過濾的資料
    private void readFilterData(String type, String OrderStatement){
        String[] FROM = new String[] {"_id","title","content","type", "date", "priority"};
        String OrderBy = (OrderStatement != null) ? " ORDER BY "+OrderStatement : "";

        //String PreparedStatement, String[] Parameters
        String PreparedStatement = "";
        String[] Parameters = null;

        ArrayList<String> preparedState = new ArrayList<>();
        ArrayList<String> parms = new ArrayList<>();
        HashMap<String, String> tempFilterMethod = new HashMap<>(filterMethod);

        // 取得快取中的查詢參數
        tempFilterMethod.forEach((key, value) -> {
            if(value != null){
                if(!key.equals("title") && !key.equals("content")) {
                    preparedState.add(key + "=?");
                    parms.add(value);
                }
            }
        });

        // 如果有查詢參數
        if(parms.size() > 0){
            // 將 LIKE 相關的查詢分離於原本的查詢參數中 (因為LIKE查詢的語法是 col LIKE ? 而非 col = ?)
            if(tempFilterMethod.containsKey("title") && tempFilterMethod.containsKey("content")){
                String title = tempFilterMethod.get("title");
                String content = tempFilterMethod.get("content");

                // 將 title 及 content 查詢從參數快取中移除
                tempFilterMethod.remove("title");
                tempFilterMethod.remove("content");

                // 建立預查詢參數
                ArrayList<String> preparedStateA = new ArrayList<>(preparedState);
                ArrayList<String> preparedStateB = new ArrayList<>(preparedState);
                preparedStateA.add("title LIKE ?");
                preparedStateB.add("content LIKE ?");

                String condA = String.join(" AND ", preparedStateA);
                String condB = String.join(" AND ", preparedStateB);
                PreparedStatement = " WHERE " + condA + " OR " + condB;

                // 為每個參數區塊增加預查詢參數 (因為其他查詢之間是用AND作連結，LIKE查詢之間要用OR作連接)
                ArrayList<String> tempParms = new ArrayList<>(parms);
                parms.clear();
                for (String p: tempParms) {
                    parms.add(p);
                }
                parms.add("%"+title+"%");


                for (String p: tempParms) {
                    parms.add(p);
                }
                parms.add("%"+content+"%");

                // 將預查詢參數陣列轉為字串陣列
                Parameters = parms.toArray(new String[0]);
            }
            else {
                // 如果沒有使用搜尋參數，就將所有參數用AND作連結
                PreparedStatement = " WHERE " + String.join(" AND ", preparedState);
                Parameters = parms.toArray(new String[0]);
            }
        }

        // 建立查詢
        Cursor c = db.rawQuery("SELECT _id,title,content,type,date,priority FROM "+ tb_table + PreparedStatement + OrderBy, Parameters);

        // 將查詢結果回傳
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                R.layout.item_filter, c,
                FROM,
                new int[]{R.id.ifId, R.id.ifTitle, R.id.ifContent, R.id.ifType, R.id.ifDate, R.id.ifImportant}, 0);
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {

            public boolean setViewValue(View aView, Cursor aCursor, int aColumnIndex) {

                // 如果查詢索引是3 (分類欄位)時，將內容格式化為 #分類名稱
                if (aColumnIndex == 3) {
                    String data = aCursor.getString(aColumnIndex);
                    TextView textView = (TextView) aView;
                    textView.setText("#" + data);
                    return true;
                }

                // 如果查詢索引是4 (日期蘭為)時，如果有取得日期值，就將欄位顯示
                if (aColumnIndex == 4) {
                    String data = aCursor.getString(aColumnIndex);
                    TextView textView = (TextView) aView;
                    textView.setText(data);
                    textView.setVisibility(View.VISIBLE);
                    return true;
                }

                // 如果查詢索引是5 (是否重要欄位)時，如果有標示為重要，將內容格式化為 [重要]
                if (aColumnIndex == 5) {
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

        lvNote.setAdapter(adapter);

        // 如果SQL沒有查詢到任何結果，顯示沒有找到記事
        if(c.getCount() == 0) {
            Toast.makeText(this, "找不到任何記事 :(", Toast.LENGTH_SHORT).show();
        }
    }

    // 在 ActionBar 新增功能按鈕
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, Menu.FIRST, Menu.NONE, "排序方式");
        menu.add(Menu.NONE, Menu.FIRST + 1, Menu.NONE, "篩選記事");
        menu.add(Menu.NONE, Menu.FIRST + 2, Menu.NONE, "搜尋記事");
        return super.onCreateOptionsMenu(menu);
    }

    // 在 ActionBar 中接收事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String[] option;
        AlertDialog.Builder alert;
        switch (item.getItemId()) {
            case android.R.id.home:
                String type = this.getIntent().getStringExtra("type");
                filterMethod.remove("title");
                filterMethod.remove("content");
                readFilterData(type, orderMethod);
                setActionBarTitle();
                this.getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                break;
            case Menu.FIRST:
                // 按下 "排序方式" 時
                option = new String[]{"重要程度 ▼", "重要程度 ▲","時間 ▼", "時間 ▲"};

                alert = getChooseDialog(this, "排序方式");
                alert.setSingleChoiceItems(option, orderWhich, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 根據選擇的選項設定SQL與法中的ORDER BY排序方式
                        switch (which){
                            case 0:
                                orderMethod = "priority DESC, date DESC, _id DESC";
                                break;
                            case 1:
                                orderMethod = "priority ASC, date DESC, _id DESC";
                                break;
                            case 2:
                                orderMethod = "date DESC, priority DESC, _id DESC";
                                break;
                            case 3:
                                orderMethod = "date ASC, priority DESC, _id ASC";
                                break;
                        }
                        orderWhich = which;
                        String type = NoteActivity.this.getIntent().getStringExtra("type");
                        filterMethod.put("type",type);
                        readFilterData(type,orderMethod);
                    }
                });

                alert.show();
                break;
            case Menu.FIRST + 1:
                // 按下 "篩選記事" 時
                option = new String[]{"未完成", "已完成"};

                alert = getChooseDialog(this, "篩選記事");
                alert.setSingleChoiceItems(option, doneWhich, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 篩選完成狀態

                        doneWhich = which;
                        filterMethod.put("isDone",String.valueOf(which));

                        String type = NoteActivity.this.getIntent().getStringExtra("type");
                        readFilterData(type, orderMethod);
                    }
                });

                alert.show();
                break;
            case Menu.FIRST + 2:
                // 按下 "搜尋記事" 時
                alert = getBasicDialog(this, "搜尋記事");

                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                alert.setView(input);
                alert.setPositiveButton("搜尋", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String type = NoteActivity.this.getIntent().getStringExtra("type");
                        filterMethod.put("type",type);

                        // 如果搜尋欄位沒有輸入值，將內容從陣列中移除；反之，將查詢參數放入陣列快取中
                        if(input.getText().toString().isEmpty()) {
                            filterMethod.remove("title");
                            filterMethod.remove("content");
                        }
                        else{
                            filterMethod.put("title",input.getText().toString());
                            filterMethod.put("content",input.getText().toString());
                        }

                        // 如果有輸入值，就啟動查詢
                        if(input.getText().toString().trim().length() > 0) {
                            NoteActivity.this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                            setActionBarTitle("搜尋: "+ input.getText().toString());
                        }
                        else{
                            setActionBarTitle();
                            NoteActivity.this.getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                        }
                        readFilterData(type,orderMethod);
                    }
                });

                alert.show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    // 接收 編輯記事或是新增記事 的回呼(callback)函數
    @Override
    protected void onActivityResult(int request, int result, Intent it) {
        super.onActivityResult(request, result, it);
        if (result == RESULT_OK) {
            String type = this.getIntent().getStringExtra("type");
            filterMethod.put("type",type);
            readFilterData(type,orderMethod);
        }
    }

    private AlertDialog.Builder getChooseDialog(NoteActivity v, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(v);
        builder.setTitle(title);
        AlertDialog alert = builder.create();
        alert.setCanceledOnTouchOutside(false);

        return builder;
    }
    private AlertDialog.Builder getBasicDialog(NoteActivity v, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(v);
        builder.setTitle(title);
        return builder;
    }

    // LISTVIEW 被點選時
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if(parent.getId() == R.id.lvNote) {
            TextView c = parent.getChildAt(position - parent.getFirstVisiblePosition()).findViewById(R.id.ifId);
            String _id = c.getText().toString();

            // 編輯記事
            Intent it = new Intent(NoteActivity.this, ActionNoteActivity.class);
            // -1 表示新增記事，大於0的數字表示記事ID
            it.putExtra("action", Integer.parseInt(_id));
            startActivityForResult(it, 100);
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
        if(adapterView.getId() == R.id.lvNote) {
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

                            String type = NoteActivity.this.getIntent().getStringExtra("type");
                            filterMethod.put("type",type);
                            readFilterData(type,orderMethod);
                        }

                    })
                    .setNegativeButton("否", null)
                    .show();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        Intent it = new Intent();
        it.putExtra("action",-1);
        setResult(RESULT_OK,it);
        finish();
    }
}