package radu.signlanguageinterpreter.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import radu.signlanguageinterpreter.R
import radu.signlanguageinterpreter.databinding.FragmentLoadingBinding
import radu.signlanguageinterpreter.globals.SharedState
import kotlin.math.roundToInt

class UpdatingFragment : Fragment() {
    private lateinit var binding: FragmentLoadingBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreate(savedInstanceState)
        binding = FragmentLoadingBinding.inflate(layoutInflater)

        SharedState.downloadProgressObservable.observe(viewLifecycleOwner) {
            binding.progressIndicator.setProgressCompat(
                (100.0 * it / SharedState.totalDownloadCount.get().toDouble()).roundToInt(), true
            )
        }

        SharedState.updatingObservable.observe(viewLifecycleOwner) {
            if (!it) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, HomeFragment())
                    .commit()
            }
        }

        return binding.root
    }
}