package com.foxsite.womentrust.abuse;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.foxsite.womentrust.R;

import java.util.ArrayList;
import java.util.List;

public class SubCategoryAdapter extends BaseAdapter {
    Context context;

    private List<SubCategoryModel> subCategoryModelList;

    public SubCategoryAdapter(Context context, ArrayList<SubCategoryModel> categoryModelArrayList) {
        this.context = context;
        this.subCategoryModelList = categoryModelArrayList;
    }

    @Override
    public int getCount() {
        return subCategoryModelList.size();
    }

    @Override
    public Object getItem(int position) {
        return subCategoryModelList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.simple_text_view_item, null);
        }
        if (i >= 0 && i < subCategoryModelList.size()) {
            TextView itemTextview = view.findViewById(R.id.itemTextview);
            if (itemTextview != null) {
                itemTextview.setText(subCategoryModelList.get(i).getSubCategoryName());
            }
        }
        return view;
    }
}