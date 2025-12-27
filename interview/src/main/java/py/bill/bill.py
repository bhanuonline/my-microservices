# Step 1: Install fpdf if you don’t have it yet
#python3 -m venv venv
#source venv/bin/activate
#python bill.py
#deactivate
# pip install fpdf

from fpdf import FPDF

# Step 2: Create receipt text
receipt_text = """Receipt no. 2071780-AE1125-379335
Date: 2025-11-27
Recipient: Bhanu Pratap

Start: Marina Walk, Dubai (2025-11-27 08:42)
Finish: Business Bay Tower, Dubai (2025-11-27 09:07)

Trip Fee: 52.00 AED
Toll Road: 4.00 AED
Dubai Taxi Booking Fee: 4.00 AED
Total (AED): 60.00

Paid by: AE4FR8YHFELK28-2  -10.00
Charged Card terminal: 50.00

This is a receipt, not an invoice."""

# Step 3: Create a PDF and add the text
pdf = FPDF()
pdf.add_page()
pdf.set_font("Arial", size=12)

for line in receipt_text.split("\n"):
    pdf.cell(0, 10, txt=line, ln=True)

# Step 4: Save the PDF
pdf.output("receipt_2071780.pdf")

print("PDF created: receipt_2071780.pdf")