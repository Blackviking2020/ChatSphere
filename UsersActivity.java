package com.example.chatsphere.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chatsphere.R;
import com.example.chatsphere.adapters.UsersAdapter;
import com.example.chatsphere.databinding.ActivityUsersBinding;
import com.example.chatsphere.listeners.UserListener;
import com.example.chatsphere.models.User;
import com.example.chatsphere.utilities.Constants;
import com.example.chatsphere.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UsersActivity extends AppCompatActivity implements UserListener {
    private ActivityUsersBinding binding;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setListeners();
        getUsers();
    }

    private void setListeners(){
        binding.imageBack.setOnClickListener( v -> onBackPressed());
    }

    private void getUsers(){
        loading(true);//加载动画
        FirebaseFirestore database = FirebaseFirestore.getInstance();//获得firebase数据库实例
        //设置一个是否完成的监听器
        database.collection(Constants.KEY_COLLECTION_USERS)
                .get()
                .addOnCompleteListener( task -> {
                    loading(false);
                    String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);
                    //如果task成功且结果不为空，就创建一个空的User列表
                    if(task.isSuccessful() && task.getResult() !=null){
                        List<User> users = new ArrayList<>();
                        //遍历查询结果，对于每个QueryDocumentSnapshot，如果当前用户ID与查询结果ID相同，
                        // 说明这个QueryDocumentSnapshot是自己，跳过，其余的就存到一个新的User对象中
                        // 然后添加到用户列表
                        for(QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()){
                            if(currentUserId.equals(queryDocumentSnapshot.getId())){
                                continue;
                            }
                            User user = new User();
                            user.name = queryDocumentSnapshot.getString(Constants.KEY_NAME);
                            user.email = queryDocumentSnapshot.getString(Constants.KEY_EMAIL);
                            user.image = queryDocumentSnapshot.getString(Constants.KEY_IMAGE);
                            user.token = queryDocumentSnapshot.getString(Constants.KEY_FCM_TOKEN);
                            user.id = queryDocumentSnapshot.getId();
                            users.add(user);
                        }
                        // 检查用户列表是不是空的，如果不是空的，就创建一个UserAdapter
                        // 然后设置给XML文件定义好的Recycler，在页面把用户列表打印出来
                        if(users.size() > 0) {
                            UsersAdapter usersAdapter = new UsersAdapter(users, this);
                            binding.userRecyclerView.setAdapter(usersAdapter);
                            binding.userRecyclerView.setVisibility(View.VISIBLE);
                        } else {
                            //用户列表是空的话，看下面的showErrorMessage()，显示No user available
                            showErrorMessage();
                        }
                    } else {
                        showErrorMessage();
                    }
                });
    }
    private void showErrorMessage(){
        binding.textErrorMessage.setText(String.format("%s", "No user available"));
        binding.textErrorMessage.setVisibility(View.VISIBLE);
    }

    private void loading(Boolean isLoading){
        if(isLoading){
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }

    public void onUserClicked(User user){
        Intent intent = new Intent( getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
        finish();
    }
}