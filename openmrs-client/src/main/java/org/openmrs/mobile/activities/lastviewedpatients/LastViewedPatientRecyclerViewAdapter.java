/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.mobile.activities.lastviewedpatients;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rx.android.schedulers.AndroidSchedulers;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.openmrs.android_sdk.library.api.repository.PatientRepository;
import com.openmrs.android_sdk.library.api.repository.VisitRepository;
import com.openmrs.android_sdk.library.dao.PatientDAO;
import com.openmrs.android_sdk.library.models.Patient;
import com.openmrs.android_sdk.utilities.DateUtils;
import com.openmrs.android_sdk.utilities.ToastUtil;

import org.openmrs.mobile.R;

class LastViewedPatientRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final int VIEW_TYPE_ITEM = 0;
    private final int VIEW_TYPE_LOADING = 1;
    private Activity mContext;
    private ArrayList<Patient> patients;
    private Set<Integer> selectedPatientPositions;
    private boolean isAllSelected = false;
    private boolean isLongClicked = false;
    private boolean enableDownload = true;
    private ActionMode actionMode;
    private LastViewedPatientsFragment view;

    LastViewedPatientRecyclerViewAdapter(Activity context, List<Patient> items, LastViewedPatientsFragment view) {
        this.mContext = context;
        this.patients = (ArrayList<Patient>) items;
        this.selectedPatientPositions = new HashSet<>();
        this.view = view;
    }

    public List<Patient> getPatients() {
        return patients;
    }

    public void showLoadingMore() {
        if (patients.isEmpty() || patients.get(getItemCount() - 1) != null) {
            patients.add(null);
            notifyItemInserted(getItemCount() - 1);
        }
    }

    public void hideLoadingMore() {
        if (!patients.isEmpty() && patients.get(getItemCount() - 1) == null) {
            patients.remove(getItemCount() - 1);
            notifyItemRemoved(getItemCount());
        }
    }

    public void addMoreToList(List<Patient> patients) {
        this.patients.addAll(patients);
        notifyDataSetChanged();
    }

    public void updateList(List<Patient> patients) {
        this.patients = (ArrayList<Patient>) patients;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ITEM) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_find_last_viewed_patients, parent, false);
            return new PatientViewHolder(itemView);
        } else {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.progressbar_item, parent, false);
            return new ProgressBarViewHolder(itemView);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return patients.get(position) == null ? VIEW_TYPE_LOADING : VIEW_TYPE_ITEM;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PatientViewHolder) {
            final Patient patient = patients.get(position);

            ((PatientViewHolder) holder).setSelected(isPatientSelected(position));
            if (null != patient.getIdentifier()) {
                String patientIdentifier = String.format(mContext.getResources().getString(R.string.patient_identifier),
                    patient.getIdentifier().getIdentifier());
                ((PatientViewHolder) holder).mIdentifier.setText(patientIdentifier);
            }
            if (null != patient.getName()) {
                ((PatientViewHolder) holder).mDisplayName.setText(patient.getName().getNameString());
            } else if (null != patient.getDisplay()) {
                /* if name is null, then we can get the name from 'display' which contains the ID and name
                separated by a hyphen( - ). */
                String patientName = patient.getDisplay().split("-")[1];
                ((PatientViewHolder) holder).mDisplayName.setText(patientName);
            }
            if (null != patient.getGender()) {
                ((PatientViewHolder) holder).mGender.setText(patient.getGender());
            }
            try {
                ((PatientViewHolder) holder).mBirthDate.setText(DateUtils.convertTime(DateUtils.convertTime(patient.getBirthdate())));
            } catch (Exception e) {
                ((PatientViewHolder) holder).mBirthDate.setText(" ");
            }

            if (null != ((PatientViewHolder) holder).mAvailableOfflineCheckbox) {
                setUpCheckBoxLogic(((PatientViewHolder) holder), patient);
            }
        } else {
            ((ProgressBarViewHolder) holder).progressBar.setIndeterminate(true);
        }
    }

    private boolean isPatientSelected(int position) {
        for (Integer selectedPatientPosition : selectedPatientPositions) {
            if (selectedPatientPosition.equals(position)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof PatientViewHolder) {
            ((PatientViewHolder) holder).clearAnimation();
        }
    }

    @Override
    public int getItemCount() {
        return patients == null ? 0 : patients.size();
    }

    class PatientViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener, View.OnClickListener {
        private CardView mRowLayout;
        private TextView mIdentifier;
        private TextView mDisplayName;
        private TextView mGender;
        private TextView mBirthDate;
        private CheckBox mAvailableOfflineCheckbox;
        private ColorStateList cardBackgroundColor;

        public PatientViewHolder(View itemView) {
            super(itemView);
            mRowLayout = (CardView) itemView;
            mIdentifier = itemView.findViewById(R.id.lastViewedPatientIdentifier);
            mDisplayName = itemView.findViewById(R.id.lastViewedPatientDisplayName);
            mGender = itemView.findViewById(R.id.lastViewedPatientGender);
            mBirthDate = itemView.findViewById(R.id.lastViewedPatientBirthDate);
            mAvailableOfflineCheckbox = itemView.findViewById(R.id.offlineCheckbox);
            mRowLayout.setOnClickListener(this);
            mRowLayout.setOnLongClickListener(this);

            cardBackgroundColor = mRowLayout.getCardBackgroundColor();
        }

        public void clearAnimation() {
            mRowLayout.clearAnimation();
        }

        @Override
        public void onClick(View view) {
            if (isLongClicked) {
                setSelected(!mRowLayout.isSelected());
            }
        }

        private void setSelected(boolean select) {
            if (select) {
                if (!enableDownload) {
                    toggleDownloadButton();
                }
                selectedPatientPositions.add(getAdapterPosition());
                this.mRowLayout.setSelected(true);
                mRowLayout.setCardBackgroundColor(mContext.getResources().getColor(R.color.selected_card));
            } else {
                removeIdFromSelectedIds(getAdapterPosition());
                this.mRowLayout.setSelected(false);
                mRowLayout.setCardBackgroundColor(cardBackgroundColor);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (v.isSelected()) {
                setSelected(false);
                mRowLayout.setCardBackgroundColor(cardBackgroundColor);
            } else {
                if (!isLongClicked) {
                    startActionMode();
                }
                isLongClicked = true;
                setSelected(true);
                mRowLayout.setCardBackgroundColor(mContext.getResources().getColor(R.color.selected_card));
                notifyDataSetChanged();
            }
            return true;
        }
    }

    class ProgressBarViewHolder extends RecyclerView.ViewHolder {
        private ProgressBar progressBar;

        public ProgressBarViewHolder(View itemView) {
            super(itemView);
            progressBar = itemView.findViewById(R.id.recycleviewProgressbar);
        }
    }

    private void removeIdFromSelectedIds(Integer position) {
        Set<Integer> newSet = new HashSet<>();
        for (Integer selectedPatientsId : selectedPatientPositions) {
            if (!selectedPatientsId.equals(position)) {
                newSet.add(selectedPatientsId);
            }
        }
        selectedPatientPositions = newSet;
        if (selectedPatientPositions.size() == 0 && isLongClicked && enableDownload) {
            toggleDownloadButton();
        }
    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.setTitle(mContext.getString(R.string.download_multiple));
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.download_multiple, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_select_all:
                    if (isAllSelected) {
                        unselectAll();
                    } else {
                        selectAll();
                    }
                    break;
                case R.id.action_download:
                    downloadSelectedPatients();
                    finish(mode);
                    break;
                case R.id.close_context_menu:
                    unselectAll();
                    finish(mode);
                    break;
                default:
                    unselectAll();
                    break;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            unselectAll();
            isLongClicked = false;
        }

        public void finish(ActionMode mode) {
            mode.finish();
            isLongClicked = false;
        }
    };

    public void startActionMode() {
        actionMode = mContext.startActionMode(mActionModeCallback);
        isLongClicked = true;
    }

    public void finishActionMode() {
        if (actionMode != null) {
            actionMode.finish();
            isLongClicked = false;
        }
    }

    public void selectAll() {
        for (int i = 0; i < patients.size(); i++) {
            selectedPatientPositions.add(i);
        }
        isAllSelected = true;
        notifyDataSetChanged();
    }

    public void unselectAll() {
        selectedPatientPositions.clear();
        isAllSelected = false;
        notifyDataSetChanged();
    }

    public void downloadSelectedPatients() {
        ToastUtil.showShortToast(mContext, ToastUtil.ToastType.NOTICE, R.string.download_started);
        for (Integer selectedPatientPosition : selectedPatientPositions) {
            downloadPatient(patients.get(selectedPatientPosition), false);
        }
        notifyDataSetChanged();
    }

    public void setUpCheckBoxLogic(final PatientViewHolder holder, final Patient patient) {
        if (isLongClicked) {
            holder.mAvailableOfflineCheckbox.setVisibility(View.INVISIBLE);
        } else {
            holder.mAvailableOfflineCheckbox.setChecked(false);
            holder.mAvailableOfflineCheckbox.setVisibility(View.VISIBLE);
            holder.mAvailableOfflineCheckbox.setButtonDrawable(R.drawable.ic_download);
            holder.mAvailableOfflineCheckbox.setText(mContext.getString(R.string.find_patients_row_checkbox_download_label));
            holder.mAvailableOfflineCheckbox.setOnClickListener(view -> {
                if (!isLongClicked && ((CheckBox) view).isChecked()) {
                    downloadPatient(patient, true);
                    disableCheckBox(holder);
                }
            });
        }
    }

    private void downloadPatient(final Patient patient, final Boolean showSnackBar) {
        new PatientRepository().downloadPatientByUuid(patient.getUuid())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        newPatient -> new PatientDAO().savePatient(newPatient)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(id -> {
                                    new VisitRepository().syncVisitsData(newPatient);
                                    new VisitRepository().syncLastVitals(newPatient.getUuid());
                                    patients.remove(patient);
                                    notifyDataSetChanged();
                                    if (showSnackBar) {
                                        view.showOpenPatientSnackbar(id);
                                    }
                                }),
                        throwable -> ToastUtil.error(mContext.getString(R.string.failed_fetching_patient_data_error_message))
                );
    }

    public void disableCheckBox(PatientViewHolder holder) {
        holder.mAvailableOfflineCheckbox.setChecked(true);
        holder.mAvailableOfflineCheckbox.setClickable(false);
        holder.mAvailableOfflineCheckbox.setButtonDrawable(R.drawable.ic_offline);
    }

    private void toggleDownloadButton() {
        if (actionMode != null) {
            MenuItem item = actionMode.getMenu().findItem(R.id.action_download);
            if (item.isEnabled()) {
                enableDownload = false;
                item.setEnabled(false);
                item.getIcon().setAlpha(128);
            } else {
                enableDownload = true;
                item.setEnabled(true);
                item.getIcon().setAlpha(255);
            }
        }
    }
}
