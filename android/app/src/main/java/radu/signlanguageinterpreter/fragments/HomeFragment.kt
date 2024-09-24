package radu.signlanguageinterpreter.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.R
import radu.signlanguageinterpreter.activities.AnimationActivity
import radu.signlanguageinterpreter.activities.AuthenticateActivity
import radu.signlanguageinterpreter.activities.CameraActivity
import radu.signlanguageinterpreter.databinding.FragmentHomeBinding
import radu.signlanguageinterpreter.io.HttpClient
import radu.signlanguageinterpreter.io.UpdateManager

class HomeFragment : Fragment() {
    private lateinit var binding: FragmentHomeBinding
    private lateinit var activity: Activity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        super.onCreate(savedInstanceState)
        binding = FragmentHomeBinding.inflate(layoutInflater)

        activity = requireActivity()

        binding.btnMoreOptions.setOnClickListener { v ->
            val overflowMenu = PopupMenu(requireActivity(), v)
            overflowMenu.menuInflater.inflate(R.menu.home_menu, overflowMenu.menu)
            overflowMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_logout -> {
                        handleLogout()
                        return@setOnMenuItemClickListener true
                    }

                    else -> return@setOnMenuItemClickListener false
                }
            }
            overflowMenu.show()
        }

        binding.update.setOnClickListener {
            UpdateManager.doUpdate(requireActivity())
        }

        binding.roToSl.setOnClickListener {
            with(requireActivity()) {
                startActivity(Intent(this, AnimationActivity::class.java))
                finish()
                setResult(Activity.RESULT_OK)
            }
        }

        binding.slToRo.setOnClickListener {
            with(requireActivity()) {
                startActivity(Intent(this, CameraActivity::class.java))
                finish()
                setResult(Activity.RESULT_OK)
            }
        }

        return binding.root
    }

    private fun handleLogout() {
        lifecycleScope.launch(Dispatchers.IO) {
            activity.runOnUiThread {
                binding.btnMoreOptions.isEnabled = false
                binding.roToSl.isEnabled = false
                binding.slToRo.isEnabled = false
                binding.update.isEnabled = false
                binding.loading.visibility = View.VISIBLE
            }

            val result = HttpClient.logout()

            activity.runOnUiThread {
                binding.loading.visibility = View.GONE
                binding.btnMoreOptions.isEnabled = true
                binding.roToSl.isEnabled = true
                binding.slToRo.isEnabled = true
                binding.update.isEnabled = true
            }

            if (result.isFailure) {
                activity.runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        Application.getString(R.string.unknown_error),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } else {
                activity.startActivity(
                    Intent(
                        activity, AuthenticateActivity::class.java
                    )
                )
                activity.finish()
                activity.setResult(Activity.RESULT_OK)
            }
        }
    }
}