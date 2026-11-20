package com.foxsite.womentrust.abuse;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;

import com.foxsite.womentrust.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SubCategoryViolenceAdapter extends BaseAdapter {

    private final Context context;
    private final List<SubCategoryViolenceModel> items;
    private final HashMap<Integer, Boolean> checkedStates;

    public SubCategoryViolenceAdapter(Context context, List<SubCategoryViolenceModel> items) {
        this.context = context;
        this.items = items;
        this.checkedStates = new HashMap<>();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public List<SubCategoryViolenceModel> getCheckedItems() {
        List<SubCategoryViolenceModel> checkedItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (checkedStates.get(i) != null && checkedStates.get(i)) {
                checkedItems.add(items.get(i));
            }
        }
        return checkedItems;
    }

    public void setSelectedViolations(List<String> selectedNames) {
        if (selectedNames == null || items == null) return;
        for (int i = 0; i < items.size(); i++) {
            SubCategoryViolenceModel model = items.get(i);
            if (model != null && model.getSub_category() != null) {
                String subCat = model.getSub_category().trim();
                for (String name : selectedNames) {
                    if (name != null && (subCat.equalsIgnoreCase(name.trim()) || subCat.toLowerCase().contains(name.toLowerCase().trim()) || name.toLowerCase().contains(subCat.toLowerCase()))) {
                        checkedStates.put(i, true);
                        break;
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_violence, parent, false);
        }
        CheckBox checkBox = view.findViewById(R.id.checkBox);

        SubCategoryViolenceModel model = items.get(position);
        checkBox.setText(model.getSub_category());

        // Null listener before setting checked state to avoid triggering recycled listener
        checkBox.setOnCheckedChangeListener(null);
        boolean isChecked = checkedStates.get(position) != null && checkedStates.get(position);
        checkBox.setChecked(isChecked);

        checkBox.setOnCheckedChangeListener((buttonView, checked) -> {
            checkedStates.put(position, checked);
        });

        view.setOnClickListener(v -> checkBox.toggle());

        return view;
    }
}
