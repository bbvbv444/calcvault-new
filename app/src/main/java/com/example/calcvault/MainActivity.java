package com.example.calcvault;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.media.MediaPlayer;
import android.os.*;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.text.method.HideReturnsTransformationMethod;
import android.view.*;
import android.widget.*;
import android.content.pm.PackageManager;
import java.io.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final String PREFS="calc_prefs", KEY_HASH="key_hash", PRO="pro", PRO_UNTIL="pro_until", NOTES="notes", LINKS="links", FILES="files", CONTACTS="contacts", FAVORITES="favorites", THEME="theme", CURRENCY_CODES="currency_codes", CURRENCY_RATES="currency_rates", CURRENCY_UPDATED="currency_updated";
    private static final int FREE_NOTES=5, FREE_LINKS=5, FREE_FILES=3;
    private static final int PICK_FILE=44, PICK_BACKUP=45, DELETE_REQUEST=46, REQUEST_AUDIO=47, PICK_SCAN=48, REQUEST_NOTIFICATIONS=49;
    private final ArrayList<String> notes=new ArrayList<>(), links=new ArrayList<>(), files=new ArrayList<>(), contacts=new ArrayList<>();
    private final HashSet<String> favorites=new HashSet<>();
    private SharedPreferences prefs;
    private LinearLayout root, body;
    private TextView display, resultDisplay;
    private String current="", operator="", expression="";
    private double stored=0;
    private boolean fresh=true, unlocked=false;
    private long unlockAt=0;
    private final Handler lockHandler=new Handler(Looper.getMainLooper());
    private final Runnable lockRunnable=()->checkLock();
    private android.media.MediaRecorder recorder;
    private File recordingFile;
    private Uri pendingDeleteUri;
    private String pendingDeleteRaw;
    private boolean pendingHideLauncher=false;
    private int bg=Color.rgb(24,29,38), panel=Color.rgb(43,51,65), fg=Color.WHITE, muted=Color.rgb(195,201,212), accent=Color.rgb(100,165,255);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        applySelectedTheme();
        loadLists();
        buildHome();
        if(hasKey()) handleIncomingShare(getIntent());
        if(!hasKey()) new Handler().postDelayed(this::createKey,300);
    }

    @Override protected void onStop(){
        super.onStop();
        if(unlocked){
            unlocked=false;
            lockHandler.removeCallbacks(lockRunnable);
            buildHome();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQUEST_AUDIO){if(grantResults.length>0&&grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)recordAudio();else toast("Microphone permission is needed to record audio");}else if(requestCode==PICK_SCAN){if(grantResults.length>0&&grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)openScannerCamera();else toast("Camera permission is needed to scan documents");}else if(requestCode==REQUEST_NOTIFICATIONS){if(grantResults.length>0&&grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED&&pendingHideLauncher)hideCalcVaultLauncher();else if(pendingHideLauncher)toast("Notification permission is required so you can reopen CalcVault after hiding its launcher icon");pendingHideLauncher=false;}
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if(hasKey()) handleIncomingShare(intent);
    }

    private void handleIncomingShare(Intent intent){
        if(intent==null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String type=intent.getType();
        if(type==null || !type.startsWith("audio/")) return;
        Uri source=null;
        if(Build.VERSION.SDK_INT>=33){
            source=intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class);
        }else{
            source=(Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if(source==null) return;
        savePrivateFile(source);
        intent.setAction(null);
        intent.removeExtra(Intent.EXTRA_STREAM);
    }

    private void buildHome(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg);
        root.setPadding(dp(12),dp(12),dp(12),dp(12));
        TextView top=label("Calculator",20,fg,true); root.addView(top,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout displayBox=new LinearLayout(this); displayBox.setOrientation(LinearLayout.VERTICAL); displayBox.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        HorizontalScrollView equationScroll=new HorizontalScrollView(this); equationScroll.setHorizontalScrollBarEnabled(false); equationScroll.setFillViewport(true);
        display=label("",30,fg,false); display.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); display.setSingleLine(true); display.setHorizontallyScrolling(true); display.setTextIsSelectable(false);
        equationScroll.addView(display,new HorizontalScrollView.LayoutParams(-2,dp(58)));
        resultDisplay=label("0",42,fg,false); resultDisplay.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); resultDisplay.setSingleLine(true);
        displayBox.addView(equationScroll,new LinearLayout.LayoutParams(-1,dp(62))); displayBox.addView(resultDisplay,new LinearLayout.LayoutParams(-1,dp(70)));
        root.addView(displayBox,new LinearLayout.LayoutParams(-1,0,1));
        GridLayout grid=new GridLayout(this); grid.setColumnCount(4);
        String[][] keys={{"C","⌫","%","÷"},{"7","8","9","×"},{"4","5","6","−"},{"1","2","3","+"},{"0",".","=",""}};
        for(String[] row:keys) for(String k:row){
            if(k.isEmpty()) continue;
            Button b=button(k); b.setOnClickListener(v->calcKey(((Button)v).getText().toString()));
            GridLayout.LayoutParams cp=cellParams();
            if("=".equals(k)) cp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,2,1f);
            grid.addView(b,cp);
        }
        root.addView(grid,new LinearLayout.LayoutParams(-1,dp(5*70)));
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER);
        Button tools=button("Tools"); Button settings=button("Settings");
        tools.setOnClickListener(v->toolsDialog()); settings.setOnClickListener(v->settingsDialog());
        bar.addView(tools,new LinearLayout.LayoutParams(0,dp(48),1)); bar.addView(settings,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(bar);
        setContentView(root);
    }

    private GridLayout.LayoutParams cellParams(){
        GridLayout.LayoutParams p=new GridLayout.LayoutParams(); p.width=0;p.height=dp(64);
        p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(dp(3),dp(3),dp(3),dp(3)); return p;
    }

    private void calcKey(String k){
        if("C".equals(k)){current="";operator="";stored=0;fresh=true;expression="";show("0");return;}
        if("⌫".equals(k)){
            if(fresh){
                // After a completed calculation, backspace starts a fresh entry.
                current="";operator="";expression="";fresh=true;show("0");return;
            }
            if(!expression.isEmpty()){
                expression=expression.substring(0,expression.length()-1);
                syncCurrentFromExpression();
                if(!expression.isEmpty() && " +−×÷".indexOf(expression.charAt(expression.length()-1))>=0)operator=String.valueOf(expression.charAt(expression.length()-1));
                else operator="";
                show(expression.isEmpty()?"0":expression);
            }
            return;
        }
        if("%".equals(k)){
            try{
                current=fmt(Double.parseDouble(current.isEmpty()?"0":current)/100);
                expression=current;
                operator="";
                fresh=false;
                show(expression);
            }catch(Exception ignored){}
            return;
        }
        if("=".equals(k)){
            if(hasKey() && operator.isEmpty() && !current.isEmpty() && get(KEY_HASH).equals(hash(current))){
                unlocked=true;unlockAt=System.currentTimeMillis();current="";expression="";show("0");vault();return;
            }
            calculate();return;
        }
        if(Arrays.asList("+","−","×","÷").contains(k)){
            if(current.isEmpty() && !expression.isEmpty() && " +−×÷".indexOf(expression.charAt(expression.length()-1))>=0){
                expression=expression.substring(0,expression.length()-1);
            }
            expression=expression.replaceAll("[+−×÷]$","")+k;
            current="";
            operator=k;
            fresh=false;
            show(expression);
            return;
        }
        if(".".equals(k) && current.contains("."))return;
        if(fresh){current="";expression="";operator="";fresh=false;}
        current+=k;
        expression+=k;
        show(expression);
    }

    private void syncCurrentFromExpression(){
        current="";
        if(expression.isEmpty())return;
        int i=expression.length()-1;
        while(i>=0 && (Character.isDigit(expression.charAt(i))||expression.charAt(i)=='.'))i--;
        current=expression.substring(i+1);
    }

    private void calculate(){
        String exp=expression;
        if(exp.isEmpty())return;
        try{
            double x=evaluateExpression(exp);
            String result=fmt(x);
            show(exp+"\n"+result);
            current=result;operator="";fresh=true;
        }catch(Exception e){show(exp+"\nError");current="";operator="";fresh=true;}
    }

    private double evaluateExpression(String exp){
        ArrayList<Double> nums=new ArrayList<>();ArrayList<Character> ops=new ArrayList<>();StringBuilder n=new StringBuilder();
        for(int i=0;i<exp.length();i++){char ch=exp.charAt(i);
            if(Character.isDigit(ch)||ch=='.'||(ch=='-'&&i==0))n.append(ch);
            else if(ch=='+'||ch=='−'||ch=='×'||ch=='÷'){if(n.length()==0)throw new IllegalArgumentException();nums.add(Double.parseDouble(n.toString()));ops.add(ch);n.setLength(0);}
            else throw new IllegalArgumentException();
        }
        if(n.length()==0)throw new IllegalArgumentException();nums.add(Double.parseDouble(n.toString()));
        for(int i=0;i<ops.size();)if(ops.get(i)=='×'||ops.get(i)=='÷'){double a=nums.get(i),b=nums.get(i+1);if(ops.get(i)=='÷'&&b==0)throw new ArithmeticException();nums.set(i,ops.get(i)=='×'?a*b:a/b);nums.remove(i+1);ops.remove(i);}else i++;
        double total=nums.get(0);for(int i=0;i<ops.size();i++)total=ops.get(i)=='+'?total+nums.get(i+1):total-nums.get(i+1);return total;
    }

    private void createKey(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),0,dp(24),0);
        LinearLayout first=rowWithEye(); LinearLayout second=rowWithEye();
        EditText a=(EditText)first.getChildAt(0), c=(EditText)second.getChildAt(0);
        a.setHint("Create 4+ digit key"); c.setHint("Confirm key"); box.addView(first); box.addView(second);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Create private key")
            .setMessage("Use at least 4 digits. This key is also how you unlock the hidden vault.")
            .setView(box).setCancelable(false).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{
            String k=a.getText().toString();
            if(!k.matches("\\d{4,}")){a.setError("At least 4 digits");return;}
            if(!k.equals(c.getText().toString())){c.setError("Does not match");return;}
            prefs.edit().putString(KEY_HASH,hash(k)).apply(); d.dismiss(); toast("Private key created");
        }));
        d.show();
    }

    private LinearLayout rowWithEye(){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        EditText e=pinField(""); row.addView(e,new LinearLayout.LayoutParams(0,dp(58),1));
        TextView eye=label("👁",18,fg,false); eye.setGravity(Gravity.CENTER); eye.setContentDescription("Show or hide key"); eye.setBackgroundColor(Color.TRANSPARENT);
        eye.setOnClickListener(v->{ if(e.getTransformationMethod()==null)e.setTransformationMethod(PasswordTransformationMethod.getInstance());else e.setTransformationMethod(HideReturnsTransformationMethod.getInstance()); e.setSelection(e.length()); });
        row.addView(eye,new LinearLayout.LayoutParams(dp(36),dp(58))); return row;
    }

    private void vault(){
        if(!unlocked)return;
        LinearLayout v=screen("Private Vault");
        Button add=button("+ Add item"), lock=button("Lock"), hider=button("App Hider");
        add.setOnClickListener(x->addItemDialog());lock.setOnClickListener(x->{unlocked=false;buildHome();});hider.setOnClickListener(x->appHider());
        v.addView(add,new LinearLayout.LayoutParams(-1,dp(50)));v.addView(lock,new LinearLayout.LayoutParams(-1,dp(50)));v.addView(hider,new LinearLayout.LayoutParams(-1,dp(50)));
        v.addView(label("Search, favorites, contacts and private media",14,muted,false));
        EditText search=new EditText(this);search.setHint("Search vault");search.setSingleLine(true);v.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        LinearLayout listBox=new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox,new ScrollView.LayoutParams(-1,-2));
        v.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){renderItems(listBox,s.toString());}public void afterTextChanged(android.text.Editable e){}};
        search.addTextChangedListener(watcher);renderItems(listBox,"");
        setContentView(v);
        new Handler().postDelayed(this::checkLock,60000);
    }

    private void renderItems(LinearLayout v,String query){
        v.removeAllViews();String q=query==null?"":query.trim().toLowerCase(Locale.US);
        addCopiedAppsSection(v,q);
        addSection(v,"Favorites",new ArrayList<>(),q,false,true);
        addSection(v,"Notes",notes,q,false,false);addSection(v,"Links",links,q,false,false);addSection(v,"Private Contacts",contacts,q,false,false);addSection(v,"Files / Media",files,q,true,false);
    }

    private void addCopiedAppsSection(LinearLayout v,String query){
        UserHandle profile=findPrivateProfile();
        TextView h=label("Copied Apps",18,fg,true);
        h.setPadding(0,dp(16),0,dp(6));
        v.addView(h);

        if(profile==null){
            v.addView(label("No private app space yet.",14,muted,false));
            return;
        }

        try{
            android.content.pm.LauncherApps la=(android.content.pm.LauncherApps)getSystemService(LAUNCHER_APPS_SERVICE);
            List<android.content.pm.LauncherActivityInfo> activities=la.getActivityList(null,profile);
            HashSet<String> seen=new HashSet<>();
            int shown=0;

            for(android.content.pm.LauncherActivityInfo info:activities){
                String pkg=info.getApplicationInfo().packageName;
                if(pkg.equals(getPackageName())||!seen.add(pkg))continue;

                String name=String.valueOf(info.getLabel());
                if(name==null||name.equals("null")||name.trim().isEmpty())name=pkg;
                if(!query.isEmpty()&&!name.toLowerCase(Locale.US).contains(query)&&!pkg.toLowerCase(Locale.US).contains(query))continue;

                shown++;
                LinearLayout row=new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8),dp(6),dp(4),dp(6));
                row.setBackgroundColor(panel);

                ImageView icon=new ImageView(this);
                try{icon.setImageDrawable(info.getBadgedIcon(0));}catch(Exception ignored){}
                row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));

                LinearLayout textBox=new LinearLayout(this);
                textBox.setOrientation(LinearLayout.VERTICAL);
                textBox.addView(label(name,15,fg,true));
                textBox.addView(label(pkg,10,muted,false));
                row.addView(textBox,new LinearLayout.LayoutParams(0,dp(58),1));

                Button open=button("Open Copy");
                open.setTextSize(11);
                open.setOnClickListener(x->launchPrivateCopy(pkg,profile));
                row.addView(open,new LinearLayout.LayoutParams(dp(82),dp(42)));

                Button del=button("Delete Copy");
                del.setTextSize(10);
                del.setOnClickListener(x->confirmDeleteCopiedApp(pkg,name,profile));
                row.addView(del,new LinearLayout.LayoutParams(dp(82),dp(42)));

                Button original=button("Uninstall Original");
                original.setTextSize(9);
                original.setOnClickListener(x->uninstallOriginal(pkg,name));
                row.addView(original,new LinearLayout.LayoutParams(dp(100),dp(42)));

                v.addView(row,new LinearLayout.LayoutParams(-1,-2));
                Space gap=new Space(this);
                v.addView(gap,new LinearLayout.LayoutParams(1,dp(6)));
            }

            if(shown==0)v.addView(label("No copied apps yet.",14,muted,false));
        }catch(Exception e){
            v.addView(label("No copied apps yet.",14,muted,false));
        }
    }

    private void confirmDeleteCopiedApp(String pkg,String name,UserHandle profile){
        new AlertDialog.Builder(this)
            .setTitle("Delete private copy?")
            .setMessage("Only the private copy of "+name+" will be removed. The original app will stay installed.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Delete Copy",(d,w)->deleteCopiedApp(pkg,profile))
            .show();
    }

    private void deleteCopiedApp(String pkg,UserHandle profile){
        try{
            if(Build.VERSION.SDK_INT<28){
                toast("Android does not support this private-copy action on this version");
                return;
            }
            Intent i=new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            i.setData(Uri.parse("package:"+pkg));
            i.putExtra(Intent.EXTRA_USER,profile);
            i.putExtra(Intent.EXTRA_RETURN_RESULT,true);
            startActivityForResult(i,912);
        }catch(Exception e){
            toast("Android could not open the private-copy removal screen");
        }
    }

    private void uninstallOriginal(String pkg,String name){
        if(pkg.equals(getPackageName())){toast("CalcVault cannot uninstall itself here");return;}
        new AlertDialog.Builder(this)
            .setTitle("Uninstall original?")
            .setMessage("Android will show its normal uninstall confirmation for "+name+".")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Continue",(d,w)->{
                try{
                    Intent i=new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                    i.setData(Uri.parse("package:"+pkg));
                    i.putExtra(Intent.EXTRA_RETURN_RESULT,true);
                    startActivityForResult(i,913);
                }catch(Exception e){
                    toast("Android could not open the uninstall screen");
                }
            }).show();
    }

    private void addSection(LinearLayout v,String title,ArrayList<String> list,String query,boolean fileSection,boolean favoriteOnly){
        TextView h=label(title,18,fg,true);h.setPadding(0,dp(16),0,dp(6));v.addView(h);
        if(favoriteOnly){
            addFavoriteItems(v,query);
            return;
        }
        boolean any=false;
        for(String s:list){
            String shown=fileSection?fileName(s):s;
            if(!query.isEmpty()&&!shown.toLowerCase(Locale.US).contains(query))continue;
            any=true;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,dp(6),0,dp(6));
            TextView t=label("• "+shown,15,fg,false);t.setPadding(dp(10),dp(12),dp(8),dp(12));
            if(fileSection)t.setOnClickListener(x->openPrivateFile(s));
            row.addView(t,new LinearLayout.LayoutParams(0,dp(64),1));
            String type=title.equals("Notes")?"note":title.equals("Links")?"link":title.equals("Private Contacts")?"contact":"file";
            Button star=button(isFavorite(type,s)?"★":"☆");star.setTextSize(18);star.setOnClickListener(x->{toggleFavorite(type,s);renderItems(v,query);});row.addView(star,new LinearLayout.LayoutParams(dp(48),dp(44)));
            if(fileSection){
                try{
                    JSONObject m=new JSONObject(s);String mime=m.optString("mime","");
                    if(mime.startsWith("image/")||mime.startsWith("video/")){
                        Button restore=button("Restore");restore.setTextSize(11);
                        restore.setOnClickListener(x->restorePrivateMedia(s));
                        row.addView(restore,new LinearLayout.LayoutParams(dp(62),dp(40)));
                    }
                }catch(Exception ignored){}
                Button del=button("Delete");del.setTextSize(11);
                del.setOnClickListener(x->confirmDeleteVaultItem(title,s,v,query));
                row.addView(del,new LinearLayout.LayoutParams(dp(58),dp(40)));
            }
            if(title.equals("Private Contacts")){
                Button del=button("Delete");del.setTextSize(12);
                del.setOnClickListener(x->{contacts.remove(s);favorites.remove(itemKey("contact",s));saveLists();renderItems(v,query);});
                row.addView(del,new LinearLayout.LayoutParams(dp(58),dp(40)));
            }
            if(title.equals("Notes")||title.equals("Links")){
                Button del=button("Delete");del.setTextSize(12);
                del.setOnClickListener(x->confirmDeleteVaultItem(title,s,v,query));
                row.addView(del,new LinearLayout.LayoutParams(dp(72),dp(44)));
            }
            v.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        if(!any)v.addView(label(list.isEmpty()?"Nothing saved yet":"No matching items",14,muted,false));
    }

    private void confirmDeleteVaultItem(String title,String raw,LinearLayout v,String query){
        String shown=title.equals("Files / Media")?fileName(raw):raw;
        new AlertDialog.Builder(this).setTitle("Delete permanently?")
            .setMessage("This will completely remove the item from CalcVault. This cannot be undone.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{
                try{
                    if(title.equals("Files / Media")){
                        JSONObject m=new JSONObject(raw);String path=m.optString("path","");
                        if(!path.isEmpty()){File f=new File(path);if(f.exists())f.delete();}
                        files.remove(raw);favorites.remove(itemKey("file",raw));
                    }else if(title.equals("Notes")){
                        notes.remove(raw);favorites.remove(itemKey("note",raw));
                    }else if(title.equals("Links")){
                        links.remove(raw);favorites.remove(itemKey("link",raw));
                    }
                    saveLists();renderItems(v,query);toast("Deleted permanently");
                }catch(Exception e){toast("Could not delete item");}
            }).show();
    }
    private void addFavoriteItems(LinearLayout v,String query){
        boolean any=false;
        for(String s:notes)if(isFavorite("note",s)&&matches(s,query)){addFavoriteRow(v,"Note",s,query);any=true;}
        for(String s:links)if(isFavorite("link",s)&&matches(s,query)){addFavoriteRow(v,"Link",s,query);any=true;}
        for(String s:contacts)if(isFavorite("contact",s)&&matches(s,query)){addFavoriteRow(v,"Contact",s,query);any=true;}
        for(String s:files)if(isFavorite("file",s)&&matches(fileName(s),query)){addFavoriteRow(v,"File",fileName(s),query);any=true;}
        if(!any)v.addView(label("No favorites yet",14,muted,false));
    }

    private void addFavoriteRow(LinearLayout v,String type,String text,String query){
        TextView t=label("★ "+type+": "+text,15,fg,false);t.setPadding(dp(8),dp(8),dp(8),dp(8));v.addView(t);
    }

    private boolean matches(String s,String q){return q.isEmpty()||s.toLowerCase(Locale.US).contains(q);}

    private void addItemDialog(){
        String[] choices={"Note","Link","File / Image","Private contact","Record audio","Document Scanner"};
        new AlertDialog.Builder(this).setTitle("Add item").setItems(choices,(d,w)->{
            if(w==0)addText(false);else if(w==1)addText(true);else if(w==2)pickFile();else if(w==3)addPrivateContact();else if(w==4)recordAudio();else scanDocument();
        }).show();
    }

    private void addText(boolean link){
        ArrayList<String> list=link?links:notes;
        EditText e=new EditText(this);e.setHint(link?"https://example.com":"Write a note");
        if(link)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle(link?"Add link":"Add note").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String s=e.getText().toString().trim();if(!s.isEmpty()){list.add(s);saveLists();vault();}
        }).show();
    }

    private void addPrivateContact(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        EditText name=new EditText(this);name.setHint("Name");
        EditText phone=new EditText(this);phone.setHint("Phone");
        EditText email=new EditText(this);email.setHint("Email (optional)");
        box.addView(name);box.addView(phone);box.addView(email);
        new AlertDialog.Builder(this).setTitle("Private contact").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String n=name.getText().toString().trim();if(n.isEmpty()){toast("Enter a name");return;}
            try{JSONObject o=new JSONObject();o.put("name",n);o.put("phone",phone.getText().toString().trim());o.put("email",email.getText().toString().trim());contacts.add(o.toString());saveLists();toast("Private contact saved");if(unlocked)vault();}catch(Exception e){toast("Could not save contact");}
        }).show();
    }

    private void recordAudio(){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            new AlertDialog.Builder(this).setTitle("Microphone permission")
                .setMessage("CalcVault needs microphone access only when you choose Record audio. The recording is saved privately inside CalcVault.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Allow",(d,w)->requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},REQUEST_AUDIO))
                .show();
            return;
        }

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10),dp(10),dp(10),dp(10));
        box.setBackgroundColor(Color.rgb(35,43,56));
        TextView status=label("Ready to record",16,fg,true);

        LinearLayout meter=new LinearLayout(this);
        meter.setOrientation(LinearLayout.HORIZONTAL);
        meter.setGravity(Gravity.CENTER_VERTICAL);
        meter.setPadding(dp(6),dp(12),dp(6),dp(12));
        ArrayList<View> bars=new ArrayList<>();
        for(int i=0;i<24;i++){
            TextView bar=new TextView(this);
            bar.setBackgroundColor(accent);
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(6),dp(6));
            bp.setMargins(dp(2),0,dp(2),0);
            meter.addView(bar,bp);
            bars.add(bar);
        }

        Button start=button("Start Recording");
        Button stop=button("Stop & Save Recording");
        Button cancel=button("Cancel");
        stop.setVisibility(View.GONE);
        cancel.setVisibility(View.GONE);

        box.addView(status,new LinearLayout.LayoutParams(-1,dp(42)));
        box.addView(meter,new LinearLayout.LayoutParams(-1,dp(70)));
        box.addView(start,new LinearLayout.LayoutParams(-1,dp(48)));
        box.addView(stop,new LinearLayout.LayoutParams(-1,dp(48)));
        box.addView(cancel,new LinearLayout.LayoutParams(-1,dp(48)));

        final boolean[] recording={false};
        final Handler meterHandler=new Handler(Looper.getMainLooper());
        final Runnable meterRunnable=new Runnable(){
            @Override public void run(){
                if(!recording[0]||recorder==null)return;
                int amp=0;
                try{amp=recorder.getMaxAmplitude();}catch(Exception ignored){}
                float level=Math.min(1f,amp/32767f);
                if(level<0.06f)level=0.06f;
                for(int i=0;i<bars.size();i++){
                    double wave=(Math.sin(i*0.8+System.currentTimeMillis()/150.0)+1.0)/2.0;
                    int h=6+Math.round(dp(42)*(0.25f+0.75f*level*(float)wave));
                    bars.get(i).getLayoutParams().height=h;
                    bars.get(i).requestLayout();
                }
                meterHandler.postDelayed(this,100);
            }
        };

        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Private Audio Recorder")
            .setView(box)
            .setNegativeButton("Close",null)
            .create();

        dialog.setOnDismissListener(d->{
            recording[0]=false;
            meterHandler.removeCallbacks(meterRunnable);
            if(recorder!=null){
                try{recorder.stop();}catch(Exception ignored){}
                try{recorder.release();}catch(Exception ignored){}
                recorder=null;
            }
            if(recordingFile!=null&&recordingFile.exists())recordingFile.delete();
            recordingFile=null;
        });
        dialog.show();

        start.setOnClickListener(v->{
            if(recording[0])return;
            try{
                File dir=new File(getFilesDir(),"vault_files");                if(!dir.exists()&&!dir.mkdirs())throw new IOException("folder");
                File nm=new File(dir,nextHumanFileName("Private Voice Recording",".m4a"));
                recordingFile=nm;

                android.media.MediaRecorder r=new android.media.MediaRecorder();
                recorder=r;
                r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
                r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
                r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
                r.setOutputFile(nm.getAbsolutePath());
                r.prepare();
                r.start();

                recording[0]=true;
                status.setText("Recording… speak now");
                start.setVisibility(View.GONE);
                stop.setVisibility(View.VISIBLE);
                cancel.setVisibility(View.VISIBLE);
                meterHandler.post(meterRunnable);
            }catch(Exception e){
                if(recorder!=null){try{recorder.release();}catch(Exception ignored){}recorder=null;}
                if(recordingFile!=null&&recordingFile.exists())recordingFile.delete();
                recordingFile=null;
                recording[0]=false;
                status.setText("Could not start recording");
                toast("Could not start recording");
            }
        });

        stop.setOnClickListener(v->{
            if(!recording[0]||recorder==null)return;
            try{
                recording[0]=false;
                meterHandler.removeCallbacks(meterRunnable);
                recorder.stop();
                recorder.release();
                recorder=null;
                if(recordingFile!=null&&recordingFile.exists()&&recordingFile.length()>0){
                    File finishedRecording=recordingFile;
                    recordingFile=null;
                    showRecordedNoteDialog(finishedRecording,dialog);
                }else throw new IOException("empty recording");
            }catch(Exception e){
                if(recorder!=null){try{recorder.release();}catch(Exception ignored){}recorder=null;}
                if(recordingFile!=null&&recordingFile.exists())recordingFile.delete();
                recordingFile=null;
                recording[0]=false;
                meterHandler.removeCallbacks(meterRunnable);                toast("Could not save recording");
            }
        });

        cancel.setOnClickListener(v->{
            recording[0]=false;
            meterHandler.removeCallbacks(meterRunnable);
            if(recorder!=null){
                try{recorder.stop();}catch(Exception ignored){}
                try{recorder.release();}catch(Exception ignored){}
                recorder=null;
            }
            if(recordingFile!=null&&recordingFile.exists())recordingFile.delete();
            recordingFile=null;
            dialog.dismiss();
        });
    }

    private void scanDocument(){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.CAMERA},PICK_SCAN);return;}
        openScannerCamera();
    }
    private void openScannerCamera(){
        try{Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);if(i.resolveActivity(getPackageManager())==null){toast("No camera app available");return;}startActivityForResult(i,PICK_SCAN);}catch(Exception e){toast("Could not open camera");}
    }

    private void saveScannedPdf(android.graphics.Bitmap bmp){
        try{
            File dir=new File(getFilesDir(),"vault_files");if(!dir.exists()&&!dir.mkdirs())throw new IOException("folder");
            File out=new File(dir,nextHumanFileName("Scanned Document",".pdf"));android.graphics.pdf.PdfDocument doc=new android.graphics.pdf.PdfDocument();
            android.graphics.pdf.PdfDocument.PageInfo info=new android.graphics.pdf.PdfDocument.PageInfo.Builder(bmp.getWidth(),bmp.getHeight(),1).create();
            android.graphics.pdf.PdfDocument.Page page=doc.startPage(info);page.getCanvas().drawBitmap(bmp,0,0,null);doc.finishPage(page);
            try(FileOutputStream fos=new FileOutputStream(out)){doc.writeTo(fos);}doc.close();
            JSONObject m=new JSONObject();m.put("name",out.getName());m.put("mime","application/pdf");m.put("path",out.getAbsolutePath());files.add(m.toString());saveLists();toast("Scanned document saved as PDF");if(unlocked)vault();
        }catch(Exception e){toast("Could not create PDF");}
    }

    private void pickFile(){
        
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_FILE);
    }

    private void removeOriginalAfterConfirm(Uri source,String raw){
        pendingDeleteUri=source;
        pendingDeleteRaw=raw;
        try{
            Uri mediaUri=source;
            if(Build.VERSION.SDK_INT>=29){
                try{
                    Uri converted=MediaStore.getMediaUri(this,source);
                    if(converted!=null) mediaUri=converted;
                }catch(Exception ignored){}
            }
            if(Build.VERSION.SDK_INT>=30){
                ArrayList<Uri> targets=new ArrayList<>();
                targets.add(mediaUri);
                android.app.PendingIntent request=MediaStore.createDeleteRequest(getContentResolver(),targets);
                startIntentSenderForResult(request.getIntentSender(),DELETE_REQUEST,null,0,0,0);
                return;
            }
            int deleted=getContentResolver().delete(mediaUri,null,null);
            if(deleted>0){
                toast("Original removed from Gallery");
                pendingDeleteUri=null;
                pendingDeleteRaw=null;
                return;
            }
            try{
                deleted=getContentResolver().delete(source,null,null);
                if(deleted>0){
                    toast("Original removed from Gallery");
                    pendingDeleteUri=null;
                    pendingDeleteRaw=null;
                    return;
                }
            }catch(Exception ignored){}
            toast("The original could not be removed");
            pendingDeleteUri=null;
            pendingDeleteRaw=null;
        }catch(android.app.RecoverableSecurityException e){
            try{
                startIntentSenderForResult(e.getUserAction().getActionIntent().getIntentSender(),DELETE_REQUEST,null,0,0,0);
            }catch(Exception x){
                pendingDeleteUri=null;
                pendingDeleteRaw=null;
                toast("Could not open the Gallery permission screen");
            }
        }catch(SecurityException e){
            pendingDeleteUri=null;
            pendingDeleteRaw=null;
            toast("Android did not allow removal of the original");
        }catch(Exception e){
            pendingDeleteUri=null;
            pendingDeleteRaw=null;
            toast("Could not request Gallery permission");
        }
    }

    private void restorePrivateMedia(String raw){
        try{
            JSONObject m=new JSONObject(raw);File f=new File(m.getString("path"));String mime=m.optString("mime","application/octet-stream");
            if(!f.exists()){toast("This saved file is missing");return;}
            String name=m.optString("name",f.getName());boolean image=mime.startsWith("image/");
            android.content.ContentValues v=new android.content.ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);v.put(MediaStore.MediaColumns.MIME_TYPE,mime);
            if(Build.VERSION.SDK_INT>=29)v.put(MediaStore.MediaColumns.RELATIVE_PATH,image?"Pictures/CalcVault":"Movies/CalcVault");
            Uri collection=image?MediaStore.Images.Media.EXTERNAL_CONTENT_URI:MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            Uri out=getContentResolver().insert(collection,v);if(out==null)throw new IOException("Cannot create gallery item");
            try(InputStream in=new FileInputStream(f);OutputStream os=getContentResolver().openOutputStream(out)){
                if(os==null)throw new IOException("Cannot write gallery item");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)os.write(b,0,n);
            }
            files.remove(raw);f.delete();saveLists();toast("Restored to Gallery");if(unlocked)vault();
        }catch(Exception e){toast("Could not restore this file");}
    }

    @Override protected void onActivityResult(int r,int c,Intent data){
        super.onActivityResult(r,c,data);
        if(r==PICK_FILE&&c==RESULT_OK&&data!=null&&data.getData()!=null)savePrivateFile(data.getData());
        else if(r==PICK_SCAN&&c==RESULT_OK&&data!=null&&data.getExtras()!=null){android.graphics.Bitmap bmp=(android.graphics.Bitmap)data.getExtras().get("data");if(bmp!=null)saveScannedPdf(bmp);}
        else if(r==PICK_BACKUP&&c==RESULT_OK&&data!=null&&data.getData()!=null)importBackup(data.getData());
        else if(r==910){
            if(c==RESULT_OK)toast("Private App Space setup completed");
            else toast("Private App Space setup was canceled or not available");
            refreshPrivateAppsAfterProvisioning();
        }
        else if(r==DELETE_REQUEST){
            if(c==RESULT_OK){
                toast("Original removed from Gallery");
            }else{
                toast("Original kept in Gallery");
            }
            pendingDeleteUri=null;
            pendingDeleteRaw=null;
        }
    }

    private void showRecordedNoteDialog(File recordedFile, AlertDialog recorderDialog){
        final EditText input=new EditText(this);
        input.setHint("Example: Meeting voice note");
        input.setTextColor(fg);
        input.setHintTextColor(muted);
        input.setSingleLine(true);
        input.setPadding(dp(12),dp(8),dp(12),dp(8));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),0,dp(8),0);
        box.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));

        TextView count=label("0/15 words",12,muted,false);
        box.addView(count,new LinearLayout.LayoutParams(-1,dp(32)));

        input.addTextChangedListener(new android.text.TextWatcher(){
            private boolean fixing=false;
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int countChanged){}
            public void afterTextChanged(android.text.Editable e){
                if(fixing)return;
                fixing=true;
                String value=e.toString();
                String limited=limitTo15Words(value);
                if(!value.equals(limited)){
                    e.replace(0,e.length(),limited);
                    input.setSelection(e.length());
                }
                int words=countWords(e.toString());
                count.setText(words+"/15 words");
                count.setTextColor(words>=15?Color.rgb(255,110,110):muted);
                count.invalidate();
                fixing=false;
            }
        });

        AlertDialog nameDialog=new AlertDialog.Builder(this)
            .setTitle("Name this voice note")
            .setMessage("Add a short note. Maximum 15 words.")
            .setView(box)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save",null)
            .create();

        nameDialog.setOnShowListener(d->nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String note=input.getText().toString().trim();
            if(!validateMediaNote(input,count)) return;

            try{
                String base=note.replaceAll("[^A-Za-z0-9._ -]","_").trim();
                if(base.isEmpty())throw new IOException("Invalid name");
                String ext=".m4a";
                File finalFile=new File(recordedFile.getParentFile(),base+ext);
                int copyNo=2;
                while(finalFile.exists()){
                    finalFile=new File(recordedFile.getParentFile(),base+" ("+copyNo+")"+ext);
                    copyNo++;
                }
                if(!recordedFile.renameTo(finalFile))throw new IOException("Could not rename recording");

                JSONObject m=new JSONObject();
                m.put("name",note);
                m.put("mime","audio/mp4");
                m.put("path",finalFile.getAbsolutePath());
                files.add(m.toString());
                saveLists();
                toast("Private voice recording saved");
                if(recorderDialog!=null)recorderDialog.dismiss();
                if(unlocked)vault();
                nameDialog.dismiss();
            }catch(Exception e){
                toast("Could not save recording");
            }
        }));
        nameDialog.show();
    }

    private void savePrivateFile(Uri source){
        try{
            String originalName=queryDisplayName(source);
            String mime=getContentResolver().getType(source); if(mime==null)mime="application/octet-stream";
            if(mime.startsWith("image/")||mime.startsWith("video/")||mime.startsWith("audio/")){
                showMediaNoteDialog(source,originalName,mime);
                return;
            }
            savePrivateFileWithName(source,originalName,mime,null);
        }catch(Exception e){toast("Could not save that file");}
    }

    private String limitTo15Words(String text){
        if(text==null)return "";
        String value=text.trim();
        if(value.isEmpty())return "";
        String[] words=value.split("\\s+");
        if(words.length<=15)return value;
        StringBuilder out=new StringBuilder();
        for(int i=0;i<15;i++){
            if(i>0)out.append(' ');
            out.append(words[i]);
        }
        return out.toString();
    }

    private boolean validateMediaNote(EditText input, TextView count){
        String note=limitTo15Words(input.getText().toString());
        if(!note.equals(input.getText().toString())){
            input.setText(note);
            input.setSelection(input.length());
        }
        int words=countWords(note);
        count.setText(words+"/15 words");
        count.setTextColor(words>=15?Color.rgb(255,110,110):muted);
        if(words==0){
            input.setError("Please add a note");
            return false;
        }
        return true;
    }

    private void showMediaNoteDialog(Uri source,String originalName,String mime){
        final EditText input=new EditText(this);
        input.setHint("Example: School project");
        input.setTextColor(fg);
        input.setHintTextColor(muted);
        input.setSingleLine(true);
        input.setPadding(dp(12),dp(8),dp(12),dp(8));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),0,dp(8),0);
        box.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));

        TextView count=label("0/15 words",12,muted,false);
        box.addView(count,new LinearLayout.LayoutParams(-1,dp(32)));

        input.addTextChangedListener(new android.text.TextWatcher(){
            private boolean fixing=false;
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int countChanged){}
            public void afterTextChanged(android.text.Editable e){
                if(fixing)return;
                fixing=true;
                String value=e.toString();
                String limited=limitTo15Words(value);
                if(!value.equals(limited)){
                    e.replace(0,e.length(),limited);
                    input.setSelection(e.length());
                }
                int words=countWords(e.toString());
                count.setText(words+"/15 words");
                count.setTextColor(words>=15?Color.rgb(255,110,110):muted);
                count.invalidate();
                fixing=false;
            }
        });

        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Name this media")
            .setMessage("Add a short note. It will become the media name inside your private vault. Maximum 15 words.")
            .setView(box)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save",null)
            .create();

        dialog.setOnShowListener(d->{
            Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v->{
                if(!validateMediaNote(input,count)) return;
                String note=input.getText().toString().trim();
                savePrivateFileWithName(source,originalName,mime,note);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private int countWords(String text){
        String s=text==null? "":text.trim();
        if(s.isEmpty())return 0;
        return s.split("\\s+").length;
    }

    private void savePrivateFileWithName(Uri source,String originalName,String mime,String customName){
        try{
            String name=(customName==null||customName.trim().isEmpty())
                ?humanMediaName(mime,originalName)
                :customName.trim();

            String ext=extensionForMime(mime,originalName);
            String safeBase=name.replaceAll("[^A-Za-z0-9._ -]","_").trim();
            if(safeBase.isEmpty())throw new IOException("Invalid name");

            String safe=safeBase+(ext.isEmpty()?"":ext);
            File dir=new File(getFilesDir(),"vault_files"); if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create private folder");
            File noMedia=new File(dir,".nomedia"); if(!noMedia.exists())noMedia.createNewFile();

            File out=new File(dir,safe);
            int copyNo=2;
            while(out.exists()){
                out=new File(dir,safeBase+" ("+copyNo+")"+ext);
                copyNo++;
            }

            InputStream in=getContentResolver().openInputStream(source); if(in==null)throw new IOException("Cannot read selected file");
            FileOutputStream fos=new FileOutputStream(out);
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)fos.write(buf,0,n);
            in.close();fos.close();

            String displayName=customName==null||customName.trim().isEmpty()
                ?name
                :customName.trim();

            JSONObject meta=new JSONObject();
            meta.put("name",displayName);
            meta.put("mime",mime);
            meta.put("path",out.getAbsolutePath());
            String raw=meta.toString();
            files.add(raw);saveLists();
            toast("Saved privately in CalcVault");

            if(mime.startsWith("image/")||mime.startsWith("video/")){
                new AlertDialog.Builder(this).setTitle("Remove original from Gallery?")
                    .setMessage("CalcVault has safely stored a private copy. Remove the original from your Gallery so only the hidden copy remains?")
                    .setNegativeButton("Keep original",null)
                    .setPositiveButton("Remove",(d,w)->removeOriginalAfterConfirm(source,raw))
                    .show();
            }
            if(unlocked)vault();
        }catch(Exception e){toast("Could not save that file");}
    }

    private String queryDisplayName(Uri uri){
        android.database.Cursor c=null;
        try{c=getContentResolver().query(uri,new String[]{"_display_name"},null,null,null);if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}finally{if(c!=null)c.close();}
        return null;
    }

    private String fileName(String raw){
        try{
            JSONObject o=new JSONObject(raw);
            String name=o.optString("name","");
            String mime=o.optString("mime","");
            if(name.matches("\\d+_.*")||name.matches("\\d+")) return humanMediaName(mime,name);
            if(name.isEmpty()) return humanMediaName(mime,null);
            return name;
        }catch(Exception e){return "Saved file";}
    }

    private String humanMediaName(String mime,String originalName){
        String ext=extensionForMime(mime,originalName);
        String base;
        if(mime!=null&&mime.startsWith("image/")) base="Photo";
        else if(mime!=null&&mime.startsWith("video/")) base="Video";
        else if(mime!=null&&mime.startsWith("audio/")) base="Voice Note";
        else if("application/pdf".equalsIgnoreCase(mime)) base="Document";
        else base="File";
        return nextHumanFileName(base,ext);
    }

    private String extensionForMime(String mime,String originalName){
        if(originalName!=null){
            int dot=originalName.lastIndexOf('.');
            if(dot>=0&&dot<originalName.length()-1)return originalName.substring(dot).toLowerCase(Locale.US);
        }
        if(mime==null)return "";
        if("image/jpeg".equals(mime))return ".jpg";
        if("image/png".equals(mime))return ".png";
        if("image/webp".equals(mime))return ".webp";
        if("video/mp4".equals(mime))return ".mp4";
        if("audio/mp4".equals(mime))return ".m4a";
        if("audio/mpeg".equals(mime))return ".mp3";
        if("audio/wav".equals(mime))return ".wav";
        if("application/pdf".equals(mime))return ".pdf";
        return "";
    }

    private String nextHumanFileName(String base,String ext){
        File dir=new File(getFilesDir(),"vault_files");
        if(!dir.exists())dir.mkdirs();
        int n=1;
        while(true){
            String name=base+" "+n+ext;
            if(!new File(dir,name).exists())return name;
            n++;
        }
    }

    private void openPrivateFile(String raw){
        try{
            JSONObject o=new JSONObject(raw);File f=new File(o.getString("path"));
            if(!f.exists()){toast("This saved file is missing");return;}
            String mime=o.optString("mime","application/octet-stream");
            if(mime.startsWith("image/")){ viewPrivateImage(f); return; }
            if(mime.startsWith("video/")){ viewPrivateVideo(f); return; }            Uri uri=Uri.parse("content://com.example.calcvault.privatefiles/file/"+Uri.encode(f.getName()));
            Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,o.optString("mime","application/octet-stream"));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("CalcVault",uri));
            startActivity(i);
        }catch(Exception e){toast("No app can open this file type");}
    }

    private void viewPrivateImage(File f){
        try{
            android.graphics.Bitmap bitmap=BitmapFactory.decodeFile(f.getAbsolutePath());
            if(bitmap==null){toast("Could not read this image");return;}
            ImageView image=new ImageView(this);
            image.setImageBitmap(bitmap);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(bg);
            image.setPadding(dp(4),dp(4),dp(4),dp(4));

            final Dialog dialog=new Dialog(this);
            dialog.setTitle("Private image");
            dialog.setContentView(image);
            Window w=dialog.getWindow();
            if(w!=null){
                w.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams p=new WindowManager.LayoutParams();
                p.copyFrom(w.getAttributes());
                p.width=(int)(getResources().getDisplayMetrics().widthPixels*0.96f);
                p.height=(int)(getResources().getDisplayMetrics().heightPixels*0.88f);
                w.setAttributes(p);
            }
            dialog.show();
            w=dialog.getWindow();
            if(w!=null){
                WindowManager.LayoutParams p=w.getAttributes();
                p.width=(int)(getResources().getDisplayMetrics().widthPixels*0.96f);
                p.height=(int)(getResources().getDisplayMetrics().heightPixels*0.88f);
                w.setAttributes(p);
            }
        }catch(Exception e){toast("Could not open this image");}
    }

    @Override protected void onResume(){
        super.onResume();
        if(unlocked)checkLock();
    }

    @Override protected void onPause(){
        super.onPause();
        lockHandler.removeCallbacks(lockRunnable);
    }

    private void viewPrivateVideo(File f){
        try{
            VideoView video=new VideoView(this);
            video.setVideoPath(f.getAbsolutePath());
            MediaController controls=new MediaController(this);
            controls.setAnchorView(video);
            video.setMediaController(controls);
            video.setOnPreparedListener(mp->video.start());
            video.setOnErrorListener((mp,what,extra)->{toast("Could not play this video");return true;});
            new AlertDialog.Builder(this).setTitle("Private video").setView(video)
                .setPositiveButton("Close",null).show();
        }catch(Exception e){toast("Could not open this video");}
    }

    private void hideCalcVaultLauncher(){
        try{
            ComponentName alias=new ComponentName(this,getPackageName()+".CalcVaultLauncher");
            getPackageManager().setComponentEnabledSetting(alias,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
            toast("CalcVault launcher icon hidden");
        }catch(Exception e){
            toast("Could not hide the launcher icon");
        }
    }

    private void refreshPrivateAppsAfterProvisioning(){
        new Handler().postDelayed(()->{
            if(unlocked)appHider();
        },1200);
    }

    private void checkLock(){
        lockHandler.removeCallbacks(lockRunnable);        if(!unlocked)return;
        long limit=2*60*1000L;
        long remaining=limit-(System.currentTimeMillis()-unlockAt);
        if(remaining<=0){unlocked=false;buildHome();return;}
        lockHandler.postDelayed(lockRunnable,Math.min(remaining,30000L));
    }


    private void toolsDialog(){
        String[] a={"Scientific","Unit converter","Currency converter","BMI","Loan calculator","Date calculator"};
        new AlertDialog.Builder(this).setTitle("Tools").setItems(a,(d,w)->{
            if(w==0)scientific();else if(w==1)unit();else if(w==2)currency();else if(w==3)bmi();else if(w==4)loan();else dateCalc();
        }).show();
    }
    private void appHider(){
        LinearLayout box=screen("App Hider");
        Button back=button("Back to Vault");
        back.setOnClickListener(v->vault());
        box.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));

        box.addView(label("Private App Copies",18,fg,true));
        box.addView(label("Create a private APK copy inside CalcVault. No Android managed profile is used.",14,muted,false));
        box.addView(label("Private runtime preparation is enabled. The original app is not modified.",13,muted,false));

        TextView appsTitle=label("Installed apps",18,fg,true);
        appsTitle.setPadding(0,dp(18),0,dp(6));
        box.addView(appsTitle);

        EditText search=new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        box.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));

        ScrollView scroll=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list,new ScrollView.LayoutParams(-1,-2));
        box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        Runnable render=()->renderPrivateAppChoices(list,search.getText().toString(),null);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){render.run();}
            public void afterTextChanged(android.text.Editable e){}
        });
        render.run();
        setContentView(box);
    }

    private void renderPrivateAppChoices(LinearLayout list,String query,UserHandle ignoredProfile){
        list.removeAllViews();
        String q=query==null?"":query.trim().toLowerCase(Locale.US);
        PackageManager pm=getPackageManager();
        List<android.content.pm.ApplicationInfo> apps;
        try{
            apps=pm.getInstalledApplications(PackageManager.MATCH_ALL);
        }catch(Exception e){
            apps=new ArrayList<>();
        }
        Collections.sort(apps,(a,b)->{
            String an=String.valueOf(a.loadLabel(pm));
            String bn=String.valueOf(b.loadLabel(pm));
            return an.compareToIgnoreCase(bn);
        });

        ApkCloneStore store=new ApkCloneStore(this);
        HashSet<String> clonedPackages=new HashSet<>();
        for(ApkCloneStore.CloneRecord r:store.list())clonedPackages.add(r.packageName);

        int shown=0;
        for(android.content.pm.ApplicationInfo info:apps){
            String pkg=info.packageName;
            if(pkg==null||pkg.equals(getPackageName()))continue;
            String name=String.valueOf(info.loadLabel(pm));
            if(name.trim().isEmpty())name=pkg;
            if(!q.isEmpty()&&!name.toLowerCase(Locale.US).contains(q)&&!pkg.toLowerCase(Locale.US).contains(q))continue;
            shown++;

            boolean systemApp=(info.flags&android.content.pm.ApplicationInfo.FLAG_SYSTEM)!=0;
            boolean updatedSystemApp=(info.flags&android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;
            boolean cloned=clonedPackages.contains(pkg);

            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(8),dp(8),dp(8),dp(8));
            row.setBackgroundColor(panel);
            row.addView(label(name,16,fg,true));
            row.addView(label(pkg+" • "+((systemApp||updatedSystemApp)?"System app":"Installed app"),11,muted,false));

            Button action=button(cloned?"Private Copy Created":"Create Private Copy");
            action.setOnClickListener(v->{
                if(cloned){
                    new AlertDialog.Builder(this)
                        .setTitle("Private copy")
                        .setMessage("The private APK copy and runtime preparation are ready. The original app has not been modified. Full in-app launch and hiding still require the next virtualization layer.")
                        .setPositiveButton("OK",null).show();
                    return;
                }
                try{
                    ApkCloneImporter importer=new ApkCloneImporter(this);
                    ApkCloneStore.CloneRecord record=importer.importInstalledPackage(pkg);
                    toast(record.label+" copied privately");
                    appHider();
                }catch(Exception e){
                    toast("Could not create the private copy");
                }
            });
            row.addView(action,new LinearLayout.LayoutParams(-1,dp(46)));
            list.addView(row,new LinearLayout.LayoutParams(-1,-2));
            Space gap=new Space(this);
            list.addView(gap,new LinearLayout.LayoutParams(1,dp(6)));
        }
        if(shown==0)list.addView(label("No installed apps match your search.",14,muted,false));
    }

    private void launchPrivateCopy(String pkg,UserHandle profile){
        try{
            android.content.pm.LauncherApps la=(android.content.pm.LauncherApps)getSystemService(LAUNCHER_APPS_SERVICE);
            List<android.content.pm.LauncherActivityInfo> list=la.getActivityList(pkg,profile);
            if(list.isEmpty()){toast("Private copy is not available");return;}
            la.startMainActivity(list.get(0).getComponentName(),profile,null,null);
        }catch(Exception e){
            toast("Android could not launch the private copy");
        }
    }

    private void scientific(){
        EditText e=numberField("Number");String[] ops={"sin","cos","tan","sqrt","square","1/x"};
        new AlertDialog.Builder(this).setTitle("Scientific").setView(e).setItems(ops,(d,w)->{try{double n=Double.parseDouble(e.getText().toString());double r=w==0?Math.sin(Math.toRadians(n)):w==1?Math.cos(Math.toRadians(n)):w==2?Math.tan(Math.toRadians(n)):w==3?Math.sqrt(n):w==4?n*n:1/n;show(fmt(r));}catch(Exception x){toast("Enter a valid number");}}).show();
    }
    private void unit(){
        EditText e=numberField("Value");String[] u={"km → miles","miles → km","kg → lb","lb → kg","°C → °F","°F → °C"};
        new AlertDialog.Builder(this).setTitle("Unit converter").setView(e).setItems(u,(d,w)->{try{double n=Double.parseDouble(e.getText().toString());double r=w==0?n*.621371:w==1?n/0.621371:w==2?n*2.20462:w==3?n/2.20462:w==4?n*9/5+32:(n-32)*5/9;show(fmt(r));}catch(Exception x){toast("Enter a valid number");}}).show();
    }
    private void currency(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(8),dp(12),dp(8));
        EditText amount=numberField("Amount");box.addView(amount);
        Spinner from=new Spinner(this),to=new Spinner(this);
        box.addView(label("From currency",15,fg,false));box.addView(from);
        box.addView(label("To currency",15,fg,false));box.addView(to);
        TextView status=label("Loading saved rates…",14,muted,false);box.addView(status);
        TextView result=label("Enter an amount and choose currencies",18,fg,true);result.setPadding(dp(4),dp(18),dp(4),dp(8));box.addView(result);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Currency converter").setView(box).setPositiveButton("Convert",null).setNegativeButton("Close",null).create();
        final ArrayList<String> codes=new ArrayList<>();final HashMap<String,Double> usdRates=new HashMap<>();
        dialog.setOnShowListener(x->{
            Button convert=dialog.getButton(AlertDialog.BUTTON_POSITIVE);convert.setEnabled(false);
            convert.setOnClickListener(v->{
                try{
                    double value=Double.parseDouble(amount.getText().toString().trim());
                    String f=codes.get(from.getSelectedItemPosition()),t=codes.get(to.getSelectedItemPosition());
                    double rf="USD".equals(f)?1:usdRates.get(f),rt="USD".equals(t)?1:usdRates.get(t);
                    if(rf<=0||rt<=0)throw new Exception();
                    result.setText(fmt(value*(rt/rf))+" "+t);
                }catch(Exception z){result.setText("Enter a valid amount");}
            });
            if(loadCachedCurrencyData(codes,usdRates)){
                ArrayAdapter<String> adapter=currencyAdapter(codes);
                from.setAdapter(adapter);to.setAdapter(adapter);
                from.setSelection(indexOf(codes,"NGN"));to.setSelection(indexOf(codes,"USD"));
                status.setText(codes.size()+" currencies available • saved rates");
                convert.setEnabled(true);
            }else{
                status.setText("No saved rates yet — Internet needed for first update");
            }
            new Thread(()->{
                try{
                    ArrayList<String> freshCodes=new ArrayList<>();HashMap<String,Double> freshRates=new HashMap<>();
                    JSONObject list=new JSONObject(httpGetWithFallback("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/currencies.json","https://latest.currency-api.pages.dev/v1/currencies.json"));
                    JSONObject rates=new JSONObject(httpGetWithFallback("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json","https://latest.currency-api.pages.dev/v1/currencies/usd.json")).getJSONObject("usd");
                    Iterator<String> it=list.keys();while(it.hasNext()){String code=it.next().toUpperCase(Locale.US);if("USD".equals(code)||rates.has(code.toLowerCase(Locale.US)))freshCodes.add(code);}
                    if(!freshCodes.contains("USD"))freshCodes.add(0,"USD");Collections.sort(freshCodes);
                    for(String code:freshCodes)if(!"USD".equals(code))freshRates.put(code,rates.optDouble(code.toLowerCase(Locale.US),0));
                    saveCachedCurrencyData(freshCodes,freshRates);
                    runOnUiThread(()->{
                        codes.clear();codes.addAll(freshCodes);usdRates.clear();usdRates.putAll(freshRates);
                        ArrayAdapter<String> adapter=currencyAdapter(codes);
                        from.setAdapter(adapter);to.setAdapter(adapter);
                        from.setSelection(indexOf(codes,"NGN"));to.setSelection(indexOf(codes,"USD"));
                        status.setText(codes.size()+" currencies available • rates updated");
                        convert.setEnabled(true);
                    });
                }catch(Exception z){
                    runOnUiThread(()->{
                        if(!codes.isEmpty())status.setText(codes.size()+" currencies available • using saved rates");
                        else status.setText("Could not load currencies. Internet needed for first update.");
                    });
                }
            }).start();
        });
        dialog.show();
    }

    private ArrayAdapter<String> currencyAdapter(ArrayList<String> codes){
        ArrayList<String> labels=new ArrayList<>();for(String code:codes)labels.add(code+" — "+currencyName(code));
        return new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_spinner_dropdown_item,labels);
    }

    private boolean loadCachedCurrencyData(ArrayList<String> codes,HashMap<String,Double> rates){
        try{
            String savedCodes=prefs.getString(CURRENCY_CODES,"");
            String savedRates=prefs.getString(CURRENCY_RATES,"");
            if(savedCodes.isEmpty()||savedRates.isEmpty())return false;
            JSONArray a=new JSONArray(savedCodes);JSONObject r=new JSONObject(savedRates);
            for(int i=0;i<a.length();i++)codes.add(a.optString(i));
            for(String code:codes)if(!"USD".equals(code))rates.put(code,r.optDouble(code,0));
            return codes.contains("USD")&&!rates.isEmpty();
        }catch(Exception e){return false;}
    }

    private void saveCachedCurrencyData(ArrayList<String> codes,HashMap<String,Double> rates){
        JSONArray a=new JSONArray();for(String code:codes)a.put(code);
        JSONObject r=new JSONObject();for(String code:rates.keySet())try{r.put(code,rates.get(code));}catch(Exception ignored){}
        prefs.edit().putString(CURRENCY_CODES,a.toString()).putString(CURRENCY_RATES,r.toString()).putLong(CURRENCY_UPDATED,System.currentTimeMillis()).apply();
    }

    private int indexOf(ArrayList<String> a,String value){int i=a.indexOf(value);return i<0?0:i;}
    private String currencyName(String code){try{return Currency.getInstance(code).getDisplayName(Locale.US);}catch(Exception e){return code;}}
    private String httpGet(String url)throws Exception{
        java.net.HttpURLConnection con=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();
        con.setConnectTimeout(10000);con.setReadTimeout(15000);con.setRequestMethod("GET");con.setRequestProperty("User-Agent","CalcVault/1.0");
        int code=con.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);
        try(InputStream in=con.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString(StandardCharsets.UTF_8.name());}finally{con.disconnect();}
    }
    private String httpGetWithFallback(String primary,String fallback)throws Exception{
        try{return httpGet(primary);}catch(Exception first){return httpGet(fallback);}
    }

    private void bmi(){
        LinearLayout b=twoFields("Weight (kg)","Height (m)");EditText[] e=fields(b);
        new AlertDialog.Builder(this).setTitle("BMI").setView(b).setPositiveButton("Calculate",(d,w)->{try{double x=Double.parseDouble(e[0].getText().toString()),h=Double.parseDouble(e[1].getText().toString());show(fmt(x/(h*h)));}catch(Exception z){toast("Enter valid values");}}).setNegativeButton("Cancel",null).show();
    }
    private void loan(){
        LinearLayout b=threeFields("Principal","Annual rate %","Months");EditText[] e=fields(b);
        new AlertDialog.Builder(this).setTitle("Loan payment").setView(b).setPositiveButton("Calculate",(d,w)->{try{double p=Double.parseDouble(e[0].getText().toString()),rate=Double.parseDouble(e[1].getText().toString())/1200,n=Double.parseDouble(e[2].getText().toString());double pay=rate==0?p/n:p*rate/(1-Math.pow(1+rate,-n));show(fmt(pay));}catch(Exception z){toast("Enter valid values");}}).setNegativeButton("Cancel",null).show();
    }
    private void dateCalc(){new AlertDialog.Builder(this).setTitle("Date calculator").setMessage("Use your phone calendar for date picking in this first offline build.").setPositiveButton("OK",null).show();}
    private void history(){new AlertDialog.Builder(this).setTitle("History").setMessage("Recent calculator results appear on the calculator display. Full persistent history will be added with the next data layer.").setPositiveButton("OK",null).show();}

    private void settingsDialog(){
        String[] a={"Promo code","Backup data","Import data","Delete all data","Theme"};
        new AlertDialog.Builder(this).setTitle("Settings").setItems(a,(d,w)->{
            if(w==0)promo();else if(w==1)backup();else if(w==2){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_BACKUP);}else if(w==3)deleteAll();else themeDialog();
        }).show();
    }

    private void applySelectedTheme(){
        boolean dark=prefs.getString("theme_mode","dark").equals("dark");
        setTheme(dark?R.style.Theme_App:R.style.Theme_App_Light);
        if(dark){
            bg=Color.rgb(24,29,38);panel=Color.rgb(43,51,65);fg=Color.WHITE;muted=Color.rgb(195,201,212);accent=Color.rgb(100,165,255);
        }else{
            bg=Color.rgb(247,249,252);panel=Color.rgb(232,236,243);fg=Color.rgb(25,31,40);muted=Color.rgb(85,94,108);accent=Color.rgb(45,110,210);
        }
    }

    private void themeDialog(){
        String currentTheme=prefs.getString("theme_mode","dark");
        String[] choices={"Dark","Light"};
        int checked=currentTheme.equals("light")?1:0;
        new AlertDialog.Builder(this).setTitle("Theme").setSingleChoiceItems(choices,checked,(d,w)->{
            String selected=w==1?"light":"dark";
            prefs.edit().putString("theme_mode",selected).apply();
            d.dismiss();
            recreate();
        }).setNegativeButton("Cancel",null).show();
    }

    private void promo(){
        EditText e=new EditText(this);e.setHint("Promo code");e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        new AlertDialog.Builder(this).setTitle("Promo code").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{
            String s=e.getText().toString().trim().replaceAll("\\s+","").toUpperCase(Locale.US);
            if("FAMILYFREE99".equals(s)){prefs.edit().putBoolean(PRO,true).apply();toast("FAMILYFREE99 activated — lifetime ad-free access");}
            
            else toast("Invalid promo code");
        }).show();
    }

    private void upgradeDialog(){
        new AlertDialog.Builder(this).setTitle(isPro()?"Pro active":"Pro")
            .setMessage(isPro()?"Your Pro access is active.":prefs.getBoolean("PROMO10",false)?"Pro first month with FAMILYFREE10: ₦1,350.":"Pro price: ₦1,500/month. Use a valid promo code for the available offer.")
            .setPositiveButton(isPro()?"OK":"Promo code",isPro()?null:(d,w)->promo()).setNegativeButton(isPro()?"Close":"Not now",null).show();
    }

    private void backup(){
        try{
            File dir=new File(getFilesDir(),"vault_backups");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create backup folder");
            File out=new File(dir,"CalcVault-backup-"+System.currentTimeMillis()+".zip");
            ZipOutputStream z=new ZipOutputStream(new FileOutputStream(out));
            JSONObject meta=new JSONObject();meta.put("version",1);meta.put("notes",new JSONArray(notes));meta.put("links",new JSONArray(links));
            JSONArray fa=new JSONArray();
            for(String raw:files){try{JSONObject m=new JSONObject(raw);fa.put(new JSONObject().put("name",m.optString("name")).put("mime",m.optString("mime")).put("entry","files/"+new File(m.optString("path")).getName()));}catch(Exception ignored){}}
            meta.put("files",fa);
            z.putNextEntry(new ZipEntry("calcvault.json"));z.write(meta.toString(2).getBytes(StandardCharsets.UTF_8));z.closeEntry();
            for(String raw:files){try{JSONObject m=new JSONObject(raw);File f=new File(m.optString("path"));if(f.exists()){z.putNextEntry(new ZipEntry("files/"+f.getName()));try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)z.write(b,0,n);}z.closeEntry();}}catch(Exception ignored){}}
            z.close();
            Uri uri=Uri.parse("content://com.example.calcvault.privatefiles/backup/"+Uri.encode(out.getName()));
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/zip");send.putExtra(Intent.EXTRA_STREAM,uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("CalcVault backup",uri));
            startActivity(Intent.createChooser(send,"Export CalcVault backup"));
        }catch(Exception e){toast("Backup failed");}
    }

    private void importBackup(Uri source){
        try{
            File dir=new File(getFilesDir(),"vault_files");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create private folder");
            File noMedia=new File(dir,".nomedia"); if(!noMedia.exists())noMedia.createNewFile();
            File temp=new File(getCacheDir(),"import.zip");
            try(InputStream in=getContentResolver().openInputStream(source);FileOutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("Cannot read backup");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            }
            JSONObject meta=null;ArrayList<String> imported=new ArrayList<>();
            try(ZipInputStream z=new ZipInputStream(new FileInputStream(temp))){
                ZipEntry e;byte[] b=new byte[8192];
                while((e=z.getNextEntry())!=null){
                    if(e.isDirectory())continue;String n=e.getName();
                    if("calcvault.json".equals(n)){ByteArrayOutputStream bout=new ByteArrayOutputStream();int x;while((x=z.read(b))!=-1)bout.write(b,0,x);meta=new JSONObject(bout.toString(StandardCharsets.UTF_8.name()));}
                    else if(n.startsWith("files/")&&!n.contains("..")){
                        String fn=new File(n).getName();File dest=new File(dir,fn);try(FileOutputStream out=new FileOutputStream(dest)){int x;while((x=z.read(b))!=-1)out.write(b,0,x);}imported.add(dest.getAbsolutePath());
                    }
                }
            }
            if(meta==null)throw new IOException("Invalid backup");
            notes.clear();links.clear();files.clear();
            JSONArray ns=meta.optJSONArray("notes");if(ns!=null)for(int i=0;i<ns.length();i++)notes.add(ns.optString(i));
            JSONArray ls=meta.optJSONArray("links");if(ls!=null)for(int i=0;i<ls.length();i++)links.add(ls.optString(i));
            JSONArray fs=meta.optJSONArray("files");if(fs!=null)for(int i=0;i<fs.length()&&i<imported.size();i++){JSONObject m=fs.optJSONObject(i);if(m!=null){JSONObject saved=new JSONObject();saved.put("name",m.optString("name",new File(imported.get(i)).getName()));saved.put("mime",m.optString("mime","application/octet-stream"));saved.put("path",imported.get(i));files.add(saved.toString());}}
            saveLists();temp.delete();if(unlocked)vault();toast("Backup imported successfully");
        }catch(Exception e){toast("Could not import that backup");}
    }

    private void deleteAll(){
        if(!hasKey()){toast("No vault key exists");return;}
        EditText e=pinField("Enter your vault key to continue");
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Delete all data")
            .setMessage("This permanently removes the vault key, notes, links, and private files from CalcVault.")
            .setView(e).setNegativeButton("Cancel",null).setPositiveButton("Delete",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{
            String entered=e.getText().toString();
            if(entered.isEmpty()){e.setError("Enter your vault key");return;}
            if(!hash(entered).equals(get(KEY_HASH))){e.setError("Wrong vault key");return;}
            deletePrivateFiles();
            notes.clear();links.clear();files.clear();contacts.clear();favorites.clear();
            prefs.edit().clear().apply();
            unlocked=false;d.dismiss();buildHome();
            new Handler().postDelayed(this::createKey,250);
            toast("All CalcVault data deleted");
        }));
        d.show();
    }

    private String itemKey(String type,String value){return hash(type+"|"+value);}
    private boolean isFavorite(String type,String value){return favorites.contains(itemKey(type,value));}
    private void toggleFavorite(String type,String value){String k=itemKey(type,value);if(favorites.contains(k))favorites.remove(k);else favorites.add(k);saveLists();}

    private void deletePrivateFiles(){
        File dir=new File(getFilesDir(),"vault_files");File[] fs=dir.listFiles();
        if(fs!=null)for(File f:fs)if(f.isFile())f.delete();
    }

    private boolean isPro(){return true;}
    private boolean hasKey(){return !get(KEY_HASH).isEmpty();}
    private String get(String k){return prefs.getString(k,"");}
    private void loadLists(){load(notes,NOTES);load(links,LINKS);load(files,FILES);load(contacts,CONTACTS);migrateOldFileNames();String f=prefs.getString(FAVORITES,"");if(!f.isEmpty())try{JSONArray j=new JSONArray(f);for(int i=0;i<j.length();i++)favorites.add(j.optString(i));}catch(Exception ignored){}}
    private void migrateOldFileNames(){
        boolean changed=false;
        for(int i=0;i<files.size();i++){
            try{
                JSONObject o=new JSONObject(files.get(i));
                String name=o.optString("name","");
                File old=new File(o.optString("path",""));
                if(name.matches("\\d+_.*")||name.matches("\\d+")||name.endsWith("_scan.pdf")||name.endsWith("_voice_note.m4a")){
                    String friendly=humanMediaName(o.optString("mime",""),name);
                    File fresh=new File(old.getParentFile(),friendly);
                    if(old.exists()&&old.renameTo(fresh)){o.put("name",friendly);o.put("path",fresh.getAbsolutePath());files.set(i,o.toString());changed=true;}
                }
            }catch(Exception ignored){}
        }
        if(changed)saveLists();
    }

    private void load(ArrayList<String> a,String k){
        String s=prefs.getString(k,"");if(s.isEmpty())return;
        try{JSONArray j=new JSONArray(s);for(int i=0;i<j.length();i++)a.add(j.optString(i));return;}catch(Exception ignored){}
        for(String x:s.split("\\u0001",-1))if(!x.isEmpty())a.add(x);
    }
    private void saveLists(){prefs.edit().putString(NOTES,toJson(notes)).putString(LINKS,toJson(links)).putString(FILES,toJson(files)).putString(CONTACTS,toJson(contacts)).putString(FAVORITES,toJson(new ArrayList<>(favorites))).apply();}
    private String toJson(ArrayList<String>a){JSONArray j=new JSONArray();for(String x:a)j.put(x);return j.toString();}

    private String hash(String s){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte q:b)x.append(String.format(Locale.US,"%02x",q));return x.toString();}catch(Exception e){return "";}}
    private String fmt(double n){if(Double.isNaN(n)||Double.isInfinite(n))return "Error";if(n==Math.rint(n))return String.format(Locale.US,"%.0f",n);return String.format(Locale.US,"%.10f",n).replaceAll("0+$","").replaceAll("\\.$","");}
    private void show(String s){
        if(display==null||resultDisplay==null)return;
        int p=s.indexOf("\n");
        if(p>=0){display.setText(s.substring(0,p));resultDisplay.setText(s.substring(p+1));}
        else{display.setText(s);resultDisplay.setText("");}
        display.requestLayout();
        display.post(()->{
            ViewParent parent=display.getParent();
            if(parent instanceof HorizontalScrollView){
                HorizontalScrollView h=(HorizontalScrollView)parent;
                h.post(()->h.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
            }
        });
    }

    private LinearLayout screen(String title){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(14),dp(14),dp(14),dp(14));v.setBackgroundColor(bg);v.addView(label(title,22,fg,true));return v;}
    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);t.setPadding(dp(4),dp(4),dp(4),dp(4));return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(fg);b.setBackgroundColor(panel);return b;}
    private EditText pinField(String hint){EditText e=new EditText(this);e.setHint(hint);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);e.setTransformationMethod(PasswordTransformationMethod.getInstance());e.setTextColor(fg);e.setHintTextColor(muted);e.setPadding(dp(8),dp(8),dp(8),dp(8));return e;}
    private EditText numberField(String hint){EditText e=new EditText(this);e.setHint(hint);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);return e;}
    private LinearLayout twoFields(String a,String b){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.addView(numberField(a));x.addView(numberField(b));return x;}
    private LinearLayout threeFields(String a,String b,String c){LinearLayout x=twoFields(a,b);x.addView(numberField(c));return x;}
    private EditText[] fields(LinearLayout x){EditText[] r=new EditText[x.getChildCount()];for(int i=0;i<r.length;i++)r[i]=(EditText)x.getChildAt(i);return r;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}