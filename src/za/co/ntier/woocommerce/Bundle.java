package za.co.ntier.woocommerce;

import java.math.BigDecimal;
import java.util.List;

public class Bundle {

	String id;
	String name;
	List<Integer> bundleItems;
	BigDecimal subtotal;
	BigDecimal total;
	public Bundle(String id, String name, List<Integer> bundleItems, BigDecimal subtotal, BigDecimal total) {
		super();
		this.id = id;
		this.name = name;
		this.bundleItems = bundleItems;
		this.subtotal = subtotal;
		this.total = total;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List<Integer> getBundleItems() {
		return bundleItems;
	}
	public void setBundleItems(List<Integer> bundleItems) {
		this.bundleItems = bundleItems;
	}
	public BigDecimal getSubtotal() {
		return subtotal;
	}
	public void setSubtotal(BigDecimal subtotal) {
		this.subtotal = subtotal;
	}
	public BigDecimal getTotal() {
		return total;
	}
	public void setTotal(BigDecimal total) {
		this.total = total;
	}
	
}
