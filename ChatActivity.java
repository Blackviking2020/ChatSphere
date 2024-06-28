package com.example.chatsphere.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chatsphere.R;
import com.example.chatsphere.adapters.ChatAdapter;
import com.example.chatsphere.databinding.ActivityChatBinding;
import com.example.chatsphere.models.ChatMessage;
import com.example.chatsphere.models.User;
import com.example.chatsphere.utilities.Constants;
import com.example.chatsphere.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversionId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetails();
        init();
        listenMessage();
    }

    private void init(){
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                        chatMessages,
                        preferenceManager.getString(Constants.KEY_USER_ID),
                        getBitmapFromEncodedString(receiverUser.image)
                );
        binding.chatRecyclerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
    }

    //功能：发送信息，如果不是和这个用户的第一条信息的话就更新聊天记录，
    // 如果是第一条信息的话就创建聊天记录
    private void sendMessage(){
        //先创建一个HashMap用于存储用户在页面输入的消息数据
        HashMap<String, Object> message = new HashMap<>();
        //从PM里获得当前用户的ID，把值赋给sender
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        //然后将RECEIVER_ID，从输入框binding获取的文本，还有消息发送时间戳设置给message
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
        message.put(Constants.KEY_TIMESTAMP, new Date());
        //将消息传送到Firebase数据库
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        //检查一下查询到的conversionId是不是空的，那如果不是空的话说明原来已经有会话记录，
        // 那就调用updateConversion来更新会话记录
        if(conversionId != null){
            updateConversion(binding.inputMessage.getText().toString());
            //如果conversionId是空的，说明原来没有会话记录，那就构造一个新的conversion对象，把各种信息赋值给它
            //然后添加到Conversion集合里
        } else {
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constants.KEY_LAST_MESSAGE, binding.inputMessage.getText().toString());
            conversion.put(Constants.KEY_TIMESTAMP, new Date());
            addConversion(conversion);
        }
        //用户发送完信息就把输入框清空
        binding.inputMessage.setText(null);
    }

    //
    private void listenMessage(){
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    // 添加快照监听器SnapShotListener来监听发送和接收的消息，然后将消息记录打印在页面上
    //  这个eventListener 事件监听器和前面的工作逻辑是几乎一样的，
    // 如果在监听事件中发生错误，就直接返回，不会执行后面的操作，
    // 如果监听到的 value，就是查询结果，它不为空，就继续处理。
    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if(error !=null){
            return;
        }
        if(value!=null){
            int count = chatMessages.size();
            //接下来遍历所有的文档变更。
            //如果变更类型是 ADDED（就是新增消息的话），创建一个新的 ChatMessage 对象，
            // 从Document()中提取这些信息：发送者ID，接收者ID，消息内容，还有时间戳
            //然后将新的 ChatMessage 对象添加到 chatMessages 列表中。
            for(DocumentChange documentChange : value.getDocumentChanges()){
                if(documentChange.getType() == DocumentChange.Type.ADDED){
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                    chatMessage.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                    chatMessages.add(chatMessage);
                }
            }
            //接下来是按时间戳对 chatMessages 列表进行排序，确保消息按时间顺序排列。
            Collections.sort(chatMessages, (obj1, obj2)->obj1.dateObject.compareTo(obj2.dateObject));
            // 消息按时间顺序排列之后就可以更新一下页面显示，这里如果 count 为0，说明之前列表为空，需要整体更新Adapter。
            if(count==0){
                chatAdapter.notifyDataSetChanged();
            }
            //如果 count 不为0，说明列表已经有消息，只需要部分更新Adapter，接着将RecyclerView滚动到最新消息。
            else {
                chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1 );
            }
            //最后就是前端显示消息列表RecyclerView
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);
        if(conversionId == null){
            checkForConversation();
        }
    };

    //就是将Base64编码的字符串转换为位图。
    private Bitmap getBitmapFromEncodedString(String encodedImage){
        byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    //从Intent中获取接收者的详细信息并且设置视图中的名称。
    private void loadReceiverDetails(){
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receiverUser.name);
    }

    //设置返回按钮和发送按钮的点击监听器。
    private void setListeners(){
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v -> sendMessage());
    }

    //将日期格式化为可读的字符串。
    private String getReadableDateTime(Date date){
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    //将聊天记录添加到Firebase Firestore，并获取记录的ID。
    private void addConversion(HashMap<String, Object> conversation){
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversation)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }

    //更新聊天记录中的最后一条消息和时间戳。
    private void updateConversion(String message){
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, new Date()
        );
    }

    //用来检查是否存在聊天记录。
    private void checkForConversation(){
        if(chatMessages.size() != 0){
            checkForConversationRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receiverUser.id
            );
            checkForConversationRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }
    //用来检查Firebase是否存在聊天记录。
    private void checkForConversationRemotely(String senderId, String receiverId){
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompleteListener);
    }

    //conversionOnCompleteListener 事件监听器就是当Firebase Firestore查询任务完成后，
    // 检查任务是否成功以及是否返回了至少一个文档。
    // 如果条件满足，就从结果中获取第一个文档的ID，并将它的值给 conversionId 变量。
    private final OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task ->{
        if(task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size()>0){
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversionId = documentSnapshot.getId();
        }
    };
}