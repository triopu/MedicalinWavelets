package medicalin.ekg;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class VarDialog extends AppCompatDialogFragment {

    private EditText editText;
    private VarDialogListener varDialogListener;
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.layout_dialog, null);
        builder.setView(view)
                .setTitle("Input Variable")
                .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String var = editText.getText().toString();
                        varDialogListener.applyVar(var);
                    }
                });
        editText = view.findViewById(R.id.edit_input);
        return builder.create();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            varDialogListener = (VarDialogListener) context;
        }catch (ClassCastException e){
            throw new ClassCastException(context.toString()+
            "Must implement VarDialogListener");
        }
    }

    public interface VarDialogListener{
        void applyVar(String var);
    }
}
