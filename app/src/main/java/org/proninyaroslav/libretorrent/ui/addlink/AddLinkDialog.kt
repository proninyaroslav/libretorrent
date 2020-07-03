/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.proninyaroslav.libretorrent.ui.addlink

import android.app.Activity
import android.app.Dialog
import android.content.ClipboardManager
import android.content.ClipboardManager.OnPrimaryClipChangedListener
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import io.reactivex.disposables.CompositeDisposable
import org.proninyaroslav.libretorrent.R
import org.proninyaroslav.libretorrent.core.exception.NormalizeUrlException
import org.proninyaroslav.libretorrent.core.utils.Utils
import org.proninyaroslav.libretorrent.databinding.DialogAddLinkBinding
import org.proninyaroslav.libretorrent.ui.ClipboardDialog
import org.proninyaroslav.libretorrent.ui.FragmentCallback
import org.proninyaroslav.libretorrent.ui.FragmentCallback.ResultCode
import org.proninyaroslav.libretorrent.ui.addtorrent.AddTorrentActivity

class AddLinkDialog : DialogFragment() {
    private var alert: AlertDialog? = null
    private var binding: DialogAddLinkBinding? = null
    private var clipboardDialog: ClipboardDialog? = null
    private var clipboardViewModel: ClipboardDialog.SharedViewModel? = null
    private val disposables = CompositeDisposable()

    private val clipListener = OnPrimaryClipChangedListener { switchClipboardButton() }

    override fun onResume() {
        super.onResume()

        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                onBackPressed()
            }
            keyCode == KeyEvent.KEYCODE_BACK
        }
    }

    override fun onStop() {
        super.onStop()
        unsubscribeClipboardManager()
        disposables.clear()
    }

    override fun onStart() {
        super.onStart()
        subscribeClipboardManager()
        subscribeAlertDialog()
    }

    private fun subscribeAlertDialog() {
        val d =
            clipboardViewModel!!.observeSelectedItem().subscribe { item: ClipboardDialog.Item ->
                if (TAG_CLIPBOARD_DIALOG == item.dialogTag) handleUrlClipItem(item.str)
            }
        disposables.add(d)
    }

    private fun handleUrlClipItem(item: String) {
        if (TextUtils.isEmpty(item)) return
        binding?.viewModel?.link?.set(item)
    }

    private fun subscribeClipboardManager() {
        val clipboard =
            requireContext().getSystemService(Activity.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipListener)
    }

    private fun unsubscribeClipboardManager() {
        val clipboard =
            requireContext().getSystemService(Activity.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.removePrimaryClipChangedListener(clipListener)
    }

    private fun switchClipboardButton() {
        val clip = Utils.getClipData(requireContext().applicationContext)
        binding?.viewModel?.showClipboardButton?.set(clip != null)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val provider = ViewModelProvider(requireActivity())
        clipboardViewModel = provider.get(ClipboardDialog.SharedViewModel::class.java)
        val fm = childFragmentManager
        clipboardDialog =
            fm.findFragmentByTag(TAG_CLIPBOARD_DIALOG) as ClipboardDialog?
        val i = LayoutInflater.from(requireContext())
        binding = DataBindingUtil
            .inflate<DialogAddLinkBinding>(i, R.layout.dialog_add_link, null, false)
            .apply {
                viewModel = provider.get(AddLinkViewModel::class.java)
            }
        initLayoutView()
        return alert!!
    }

    private fun initLayoutView() {
        /* Dismiss error label if user has changed the text */
        binding?.link?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable) {
                binding!!.layoutLink.isErrorEnabled = false
                binding!!.layoutLink.error = null
            }
        })
        binding!!.clipboardButton.setOnClickListener { v: View? -> showClipboardDialog() }
        switchClipboardButton()
        binding?.viewModel!!.initLinkFromClipboard()
        initAlertDialog(binding!!.root)
    }

    private fun initAlertDialog(view: View) {
        val builder =
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_add_link_title)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .setView(view)
        alert = builder.create()
        alert!!.setCanceledOnTouchOutside(false)
        alert!!.setOnShowListener { dialog: DialogInterface? ->
            val addButton =
                alert!!.getButton(AlertDialog.BUTTON_POSITIVE)
            addButton.setOnClickListener { v: View? -> addLink() }
            val cancelButton =
                alert!!.getButton(AlertDialog.BUTTON_NEGATIVE)
            cancelButton.setOnClickListener { v: View? ->
                finish(
                    Intent(),
                    ResultCode.CANCEL
                )
            }
        }
    }

    private fun showClipboardDialog() {
        if (!isAdded) return
        val fm = childFragmentManager
        if (fm.findFragmentByTag(TAG_CLIPBOARD_DIALOG) == null) {
            clipboardDialog =
                ClipboardDialog.newInstance().also { it.show(fm, TAG_CLIPBOARD_DIALOG) }
        }
    }

    private fun addLink() {
        var s = binding?.viewModel!!.link.get()
        if (TextUtils.isEmpty(s)) return
        if (!checkUrlField()) return
        try {
            if (s != null) s = binding?.viewModel!!.normalizeUrl(s)
        } catch (e: NormalizeUrlException) {
            binding!!.layoutLink.isErrorEnabled = true
            binding!!.layoutLink.error = getString(R.string.invalid_url, e.message)
            binding!!.layoutLink.requestFocus()
            return
        }
        val i = Intent(requireContext(), AddTorrentActivity::class.java)
        i.putExtra(AddTorrentActivity.TAG_URI, Uri.parse(s))
        startActivity(i)
        finish(Intent(), ResultCode.OK)
    }

    private fun checkUrlField(): Boolean {
        if (TextUtils.isEmpty(binding!!.link.text)) {
            binding!!.layoutLink.isErrorEnabled = true
            binding!!.layoutLink.error = getString(R.string.error_empty_link)
            binding!!.layoutLink.requestFocus()
            return false
        }
        binding!!.layoutLink.isErrorEnabled = false
        binding!!.layoutLink.error = null
        return true
    }

    fun onBackPressed() {
        finish(Intent(), ResultCode.BACK)
    }

    private fun finish(intent: Intent, code: ResultCode) {
        alert!!.dismiss()
        (activity as? FragmentCallback)?.onFragmentFinished(this, intent, code)
    }

    companion object {
        private const val TAG_CLIPBOARD_DIALOG = "clipboard_dialog"

        @JvmStatic
        fun newInstance(): AddLinkDialog {
            val frag = AddLinkDialog()
            val args = Bundle()
            frag.arguments = args
            return frag
        }
    }
}
