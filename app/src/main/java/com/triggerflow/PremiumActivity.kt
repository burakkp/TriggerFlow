package com.triggerflow

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PremiumActivity : AppCompatActivity() {

    private lateinit var billingManager: BillingManager
    private lateinit var promoCodeManager: PromoCodeManager
    private lateinit var prefs: PreferencesManager

    private lateinit var btnLifetime: Button
    private lateinit var btnYearly: Button
    private lateinit var btnMonthly: Button
    private lateinit var btnRedeem: TextView
    private lateinit var btnRestore: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        prefs = PreferencesManager(this)
        promoCodeManager = PromoCodeManager(prefs)
        billingManager = BillingManager(this, prefs, promoCodeManager)

        initViews()
        setupListeners()
        
        lifecycleScope.launch {
            billingManager.isPremium.collect { isPremium ->
                if (isPremium) {
                    Toast.makeText(this@PremiumActivity, "You are Premium!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun initViews() {
        btnLifetime = findViewById(R.id.btnLifetime)
        btnYearly = findViewById(R.id.btnYearly)
        btnMonthly = findViewById(R.id.btnMonthly)
        btnRedeem = findViewById(R.id.btnRedeem)
        btnRestore = findViewById(R.id.btnRestore)
    }

    private fun setupListeners() {
        btnLifetime.setOnClickListener {
            initiatePurchase(BillingManager.LIFETIME)
        }

        btnYearly.setOnClickListener {
            initiatePurchase(BillingManager.SUB_YEARLY)
        }

        btnMonthly.setOnClickListener {
            initiatePurchase(BillingManager.SUB_MONTHLY)
        }

        btnRedeem.setOnClickListener {
            showPromoCodeDialog()
        }

        btnRestore.setOnClickListener {
            billingManager.updatePremiumState()
            Toast.makeText(this, "Checking for past purchases...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initiatePurchase(productId: String) {
        billingManager.queryProductDetails(listOf(productId)) { detailsList ->
            val details = detailsList.find { it.productId == productId }
            if (details != null) {
                val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
                
                runOnUiThread {
                     billingManager.launchBillingFlow(this, details, offerToken)
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Product not found or unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPromoCodeDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Redeem Code")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        builder.setPositiveButton("Redeem") { _, _ ->
            val code = input.text.toString()
            handlePromoCode(code)
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun handlePromoCode(code: String) {
        when (val result = promoCodeManager.redeemCode(code)) {
            is PromoCodeManager.RedeemResult.SuccessBypass -> {
                Toast.makeText(this, "Code redeemed! Premium Unlocked.", Toast.LENGTH_LONG).show()
                billingManager.updatePremiumState()
            }
            is PromoCodeManager.RedeemResult.SuccessDiscount -> {
                Toast.makeText(this, "Discount Applied! Select a plan.", Toast.LENGTH_SHORT).show()
            }
            is PromoCodeManager.RedeemResult.Invalid -> {
                Toast.makeText(this, "Invalid Code", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
