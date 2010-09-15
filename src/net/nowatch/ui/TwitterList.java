package net.nowatch.ui;

import net.nowatch.ui.ListItems;
import net.nowatch.R;

import android.os.Bundle;
import android.view.View;

public class TwitterList extends ListItems {
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        findViewById(R.id.btn_filter_podcast).setVisibility(View.GONE);
    }

}
