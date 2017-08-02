/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.fido.example.fidoapiexample.utils;

import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.fido.example.fidoapiexample.R;
import com.fido.example.fidoapiexample.U2FDemoActivity;

import java.util.List;
import java.util.Map;

public class SecurityTokenAdapter extends RecyclerView.Adapter<SecurityTokenAdapter.ViewHolder> {

    private List<Map<String, String>> tokens;
    private int rowLayout;
    private U2FDemoActivity mActivity;

    public SecurityTokenAdapter(List<Map<String, String>> applications, int rowLayout, U2FDemoActivity act) {
        this.tokens = applications;
        this.rowLayout = rowLayout;
        this.mActivity = act;
    }

    public void clearSecurityTokens() {
        int size = this.tokens.size();
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                tokens.remove(0);
            }

            this.notifyItemRangeRemoved(0, size);
        }
    }

    public void addSecurityToken(List<Map<String, String>> applications) {
        this.tokens.addAll(applications);
        this.notifyItemRangeInserted(0, applications.size() - 1);
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(rowLayout, viewGroup, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, final int item) {
        final Map<String, String> token = tokens.get(item);
        StringBuilder tokenValue = new StringBuilder();
        for (Map.Entry<String, String> entry : token.entrySet()) {
            tokenValue.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\n");
        }
        viewHolder.content.setText(tokenValue.toString());
        /*viewHolder.content.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Toast.makeText(v.getContext(), "security token " + item + " is clicked", Toast.LENGTH_SHORT).show();
                //v.setBackgroundResource(R.drawable.button_rect_list_normal);
                v.setSelected(true);
            }
        });*/
        viewHolder.image.setImageDrawable(mActivity.getResources().getDrawable(R.drawable.yubikey));
        viewHolder.removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                // confirm to remove it
                AlertDialog.Builder alert = new AlertDialog.Builder(mActivity);
                alert.setTitle("confirm to remove security token");
                alert.setMessage("Are you sure to delete this security token?");
                alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mActivity.removeTokenByIndexInList(item);
                    }
                });
                alert.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                alert.show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return tokens == null ? 0 : tokens.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView content;
        public ImageView image;
        public ImageView removeButton;

        public ViewHolder(View itemView) {
            super(itemView);
            content = itemView.findViewById(R.id.tokenValue);
            image = itemView.findViewById(R.id.tokenImage);
            removeButton = itemView.findViewById(R.id.removeToken);
        }

    }
}
