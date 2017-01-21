package app.bennsandoval.com.woodmin.fragments;

import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.actions.SearchIntents;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import app.bennsandoval.com.woodmin.R;
import app.bennsandoval.com.woodmin.Woodmin;
import app.bennsandoval.com.woodmin.activities.MainActivity;
import app.bennsandoval.com.woodmin.activities.OrderNew;
import app.bennsandoval.com.woodmin.adapters.CustomerAdapter;
import app.bennsandoval.com.woodmin.data.WoodminContract;
import app.bennsandoval.com.woodmin.interfaces.CustomerActions;
import app.bennsandoval.com.woodmin.models.customers.Customer;
import app.bennsandoval.com.woodmin.models.customers.Customers;
import app.bennsandoval.com.woodmin.sync.WoodminSyncAdapter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CustomersFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        SearchView.OnQueryTextListener,
        CustomerActions{

    private final String LOG_TAG = CustomersFragment.class.getSimpleName();

    private static final String ARG_SECTION_NUMBER = "section_number";

    private CustomerAdapter mAdapter;

    private SwipeRefreshLayout mSwipeLayout;
    private RecyclerView mRecyclerView;

    private static final int CUSTOMER_LOADER = 300;
    private static final String[] CUSTOMER_PROJECTION = {
            WoodminContract.CustomerEntry._ID,
            WoodminContract.CustomerEntry.COLUMN_ID,
            WoodminContract.CustomerEntry.COLUMN_JSON,
    };

    private String mQuery;
    private int mPage = 0;
    private int mSize = 50;

    public static CustomersFragment newInstance(int sectionNumber) {
        CustomersFragment fragment = new CustomersFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    public CustomersFragment() {
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        onNewIntent(getActivity().getIntent());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_customers, container, false);

        View.OnClickListener onClickListener = new View.OnClickListener(){
            @Override
            public void onClick(final View view) {
                int position = mRecyclerView.getChildAdapterPosition(view);
                mAdapter.getCursor().moveToPosition(position);
/*
                int idSelected = mAdapter.getCursor().getInt(mAdapter.getCursor().getColumnIndex(WoodminContract.CustomerEntry.COLUMN_ID));
                Intent orderIntent = new Intent(getActivity(), ProductDetail.class);
                orderIntent.putExtra("product", idSelected);
                startActivity(orderIntent);
*/
            }
        };

        mAdapter = new CustomerAdapter(getActivity().getApplicationContext(),R.layout.fragment_customer_list_item, null, onClickListener, this);
        mRecyclerView = (RecyclerView)rootView.findViewById(R.id.list_custumer);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(layoutManager);

        mRecyclerView.setAdapter(mAdapter);
        getActivity().getSupportLoaderManager().initLoader(CUSTOMER_LOADER, null, this);

        mSwipeLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mPage = 0;
                getPageCustomers();
            }
        });

        mSwipeLayout.setColorSchemeResources(R.color.holo_blue_bright,
                R.color.holo_green_light,
                R.color.holo_orange_light,
                R.color.holo_red_light);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(RecyclerView view, int scrollState) {
            }

            @Override
            public void onScrolled(RecyclerView view, int dx, int dy) {
                boolean enable = false;
                if (view != null && view.getChildCount() > 0) {
                    enable = view.getChildAt(0).getTop() == 0;
                }
                mSwipeLayout.setEnabled(enable);
            }
        });

        FloatingActionButton fab = (FloatingActionButton) rootView.findViewById(R.id.fab);
        if(fab != null) {
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    Intent orderIntent = new Intent(getActivity(), OrderNew.class);
                    startActivity(orderIntent);

                }
            });
        }
        getPageCustomers();
        return rootView;
    }


    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ((MainActivity) context).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        //menu.clear();
        super.onCreateOptionsMenu(menu,inflater);
        inflater.inflate(R.menu.customer_fragment_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        if (searchView != null) {
            List<SearchableInfo> searchables = searchManager.getSearchablesInGlobalSearch();
            SearchableInfo info = searchManager.getSearchableInfo(getActivity().getComponentName());
            for (SearchableInfo inf : searchables) {
                if (inf.getSuggestAuthority() != null && inf.getSuggestAuthority().startsWith("applications")) {
                    info = inf;
                }
            }
            searchView.setSearchableInfo(info);
            searchView.setOnQueryTextListener(this);
            searchView.setQueryHint(getActivity().getString(R.string.customer_title_search));

            if(mQuery != null && mQuery.length() > 0) {
                searchView.setQuery(mQuery, true);
                searchView.setIconifiedByDefault(false);
                searchView.performClick();
                searchView.requestFocus();
            } else {
                searchView.setIconifiedByDefault(true);
            }
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.d(LOG_TAG, "onCreateLoader");

        String sortOrder = WoodminContract.CustomerEntry.COLUMN_ID + " DESC";
        CursorLoader cursorLoader;
        Uri costumersUri = WoodminContract.CustomerEntry.CONTENT_URI;
        switch (id) {
            case CUSTOMER_LOADER:
                if(mQuery != null && mQuery.length()>0){
                    String query = WoodminContract.CustomerEntry.COLUMN_LAST_NAME + " LIKE ? OR  " +
                            WoodminContract.CustomerEntry.COLUMN_EMAIL + " LIKE ? OR  " +
                            WoodminContract.CustomerEntry.COLUMN_SHIPPING_LAST_NAME + " LIKE ? OR  " +
                            WoodminContract.CustomerEntry.COLUMN_SHIPPING_LAST_NAME + " LIKE ? OR  " +
                            WoodminContract.CustomerEntry.COLUMN_SHIPPING_FIRST_NAME + " LIKE ? OR  " +
                            WoodminContract.CustomerEntry.COLUMN_SHIPPING_PHONE+ " LIKE ? OR  " +
                            WoodminContract.CustomerEntry.COLUMN_BILLING_FIRST_NAME + " LIKE ? OR  " +
                            WoodminContract.CustomerEntry.COLUMN_BILLING_LAST_NAME + " LIKE ? OR  " +
                            WoodminContract.CustomerEntry.COLUMN_BILLING_PHONE + " LIKE ? OR  " +
                            WoodminContract.CustomerEntry.COLUMN_FIRST_NAME + " LIKE ?" ;
                    String[] parameters = new String[]{ "%"+mQuery+"%",
                            "%"+mQuery+"%",
                            "%"+mQuery+"%",
                            "%"+mQuery+"%",
                            "%"+mQuery+"%",
                            "%"+mQuery+"%",
                            "%"+mQuery+"%",
                            "%"+mQuery+"%",
                            "%"+mQuery+"%" };
                    cursorLoader = new CursorLoader(
                            getActivity().getApplicationContext(),
                            costumersUri,
                            CUSTOMER_PROJECTION,
                            query,
                            parameters,
                            sortOrder);
                } else {
                    //String query = WoodminContract.CustomerEntry.COLUMN_ENABLE + " = ?" ;
                    //String[] parameters = new String[]{ String.valueOf("1") };
                    cursorLoader = new CursorLoader(
                            getActivity().getApplicationContext(),
                            costumersUri,
                            CUSTOMER_PROJECTION,
                            null,
                            null,
                            sortOrder);
                }
                break;
            default:
                cursorLoader = null;
                break;
        }
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        switch (cursorLoader.getId()) {
            case CUSTOMER_LOADER:
                if(mSwipeLayout != null){
                    mSwipeLayout.setRefreshing(false);
                }
                mAdapter.changeCursor(cursor);
                break;
            default:
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        Log.d(LOG_TAG, "onLoaderReset");
        switch (cursorLoader.getId()) {
            case CUSTOMER_LOADER:
                mAdapter.notifyDataSetChanged();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mQuery = query;
        doSearch();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mQuery = newText;
        doSearch();
        return true;
    }

    protected void onNewIntent(Intent intent) {
        String action = intent.getAction();
        if (action != null && (action.equals(Intent.ACTION_SEARCH) || action.equals(SearchIntents.ACTION_SEARCH))) {
            mQuery = intent.getStringExtra(SearchManager.QUERY);
            mQuery = mQuery.replace(getString(R.string.customer_voice_search)+" ","");
        }
    }

    private void doSearch() {
        getActivity().getSupportLoaderManager().restartLoader(CUSTOMER_LOADER, null, this);
        getActivity().getSupportLoaderManager().getLoader(CUSTOMER_LOADER).forceLoad();
    }

    @Override
    public void sendEmail(Customer customer) {
        if(customer.getEmail() != null){
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto",customer.getEmail(), null));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "");
            startActivity(Intent.createChooser(emailIntent, "Woodmin"));
        }
    }

    @Override
    public void makeACall(Customer customer) {
        if(customer.getBillingAddress() != null){
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + customer.getBillingAddress().getPhone()));
            startActivity(callIntent);
        }
    }

    private void getPageCustomers() {
        mSwipeLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                mSwipeLayout.setRefreshing(true);
            }
        }, 2000);

        Log.v(LOG_TAG,"Orders Read Total:" + mAdapter.getItemCount() + " Page : " + mPage);

        HashMap<String, String> options = new HashMap<>();
        options.put("filter[limit]", String.valueOf(mSize));
        options.put("page", String.valueOf(mPage));

        Call<Customers> call = ((Woodmin)getActivity().getApplication()).getWoocommerceApiHandler().getCustomers(options);
        call.enqueue(new Callback<Customers>() {
            @Override
            public void onResponse(Call<Customers> call, Response<Customers> response) {
                mSwipeLayout.setRefreshing(false);
                int statusCode = response.code();
                if (statusCode == 200) {
                    final List<Customer> customers = response.body().getCustomers();
                    Log.v(LOG_TAG,"Success Customers page " + mPage + " customers " + customers.size());
                    new Thread(new Runnable() {
                        public void run() {
                            Gson gson = new Gson();
                            ArrayList<ContentValues> customersValues = new ArrayList<>();
                            for (Customer customer : customers) {

                                ContentValues customerValues = new ContentValues();
                                customerValues.put(WoodminContract.CustomerEntry.COLUMN_ID, customer.getId());
                                customerValues.put(WoodminContract.CustomerEntry.COLUMN_EMAIL, customer.getEmail());
                                customerValues.put(WoodminContract.CustomerEntry.COLUMN_FIRST_NAME, customer.getFirstName());
                                customerValues.put(WoodminContract.CustomerEntry.COLUMN_LAST_NAME, customer.getLastName());
                                customerValues.put(WoodminContract.CustomerEntry.COLUMN_USERNAME, customer.getUsername());
                                customerValues.put(WoodminContract.CustomerEntry.COLUMN_LAST_ORDER_ID, customer.getLastOrderId());

                                if (customer.getBillingAddress() != null) {
                                    customerValues.put(WoodminContract.CustomerEntry.COLUMN_BILLING_FIRST_NAME, customer.getBillingAddress().getFirstName());
                                    customerValues.put(WoodminContract.CustomerEntry.COLUMN_BILLING_LAST_NAME, customer.getBillingAddress().getLastName());
                                    if (customer.getBillingAddress().getPhone() != null) {
                                        customerValues.put(WoodminContract.CustomerEntry.COLUMN_BILLING_PHONE, customer.getBillingAddress().getPhone());
                                    }
                                }
                                if (customer.getShippingAddress() != null) {
                                    customerValues.put(WoodminContract.CustomerEntry.COLUMN_SHIPPING_FIRST_NAME, customer.getShippingAddress().getFirstName());
                                    customerValues.put(WoodminContract.CustomerEntry.COLUMN_SHIPPING_LAST_NAME, customer.getShippingAddress().getLastName());
                                    if (customer.getShippingAddress().getPhone() != null) {
                                        customerValues.put(WoodminContract.CustomerEntry.COLUMN_SHIPPING_PHONE, customer.getShippingAddress().getPhone());
                                    }
                                }
                                customerValues.put(WoodminContract.CustomerEntry.COLUMN_JSON, gson.toJson(customer));
                                customerValues.put(WoodminContract.CustomerEntry.COLUMN_ENABLE, 1);

                                customersValues.add(customerValues);
                            }

                            ContentValues[] customersValuesArray = new ContentValues[customersValues.size()];
                            customersValuesArray = customersValues.toArray(customersValuesArray);
                            int customersRowsUpdated = getContext().getContentResolver().bulkInsert(WoodminContract.CustomerEntry.CONTENT_URI, customersValuesArray);
                            Log.v(LOG_TAG, "Customers " + customersRowsUpdated + " updated");
                            getContext().getContentResolver().notifyChange(WoodminContract.CustomerEntry.CONTENT_URI, null, false);
                        }
                    }).start();
                    if(customers.size() == mSize) {
                        //getPageCustomers();
                    }
                }
                mPage++;
            }

            @Override
            public void onFailure(Call<Customers> call, Throwable t) {
                Log.v(LOG_TAG, "onFailure " + mPage + " error " + t.getMessage());
                mSwipeLayout.setRefreshing(false);
            }
        });

    }
}
