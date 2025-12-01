package br.com.metropolitana.contratosdatavenc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;

public class MainEventoContratosDataVenc implements EventoProgramavelJava{

	@Override
	public void afterDelete(PersistenceEvent arg0) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void afterInsert(PersistenceEvent event) throws Exception {
		
		JapeWrapper parceiroDAO = JapeFactory.dao(DynamicEntityNames.PARCEIRO);
	    JapeWrapper financeiroDAO = JapeFactory.dao(DynamicEntityNames.FINANCEIRO);
	    
	    DynamicVO financeiroVO = (DynamicVO) event.getVo();
	    DynamicVO parceiroVO = parceiroDAO.findByPK(financeiroVO.asBigDecimal("CODPARC"));
        
	    if ((financeiroVO.asBigDecimal("NUMCONTRATO") != null && financeiroVO.asBigDecimal("NUMCONTRATO").compareTo(BigDecimal.ZERO) != 0) || existefaturHH(financeiroVO.asBigDecimal("NUNOTA"))) {
	        LocalDate dataVencimento = null;
	        LocalDate hoje = LocalDate.now();
	     	        
	        if (parceiroVO.asBigDecimal("AD_DTCORTE_VENC") != null) {
	            int dataCorte = parceiroVO.asBigDecimal("AD_DTCORTE_VENC").intValue();
	            LocalDate dataCorteAtual = LocalDate.of(hoje.getYear(), hoje.getMonth(), dataCorte);
	            if (ChronoUnit.DAYS.between(hoje, dataCorteAtual) < 15) {
	                dataVencimento = hoje.plusDays(15);
	            } else {
	                dataVencimento = dataCorteAtual;
	            }
	        } 
	        else if (parceiroVO.asBigDecimal("AD_DIASCORRIDOSVENC") != null) {
	            int diasCorridos = parceiroVO.asBigDecimal("AD_DIASCORRIDOSVENC").intValue();
	            dataVencimento = hoje.plusDays(diasCorridos);
	        }
	        else {
	            dataVencimento = hoje.plusDays(15);
	        }
	        
	        dataVencimento = ajustarParaUltimoDiaDoMes(dataVencimento);
	        
	        Timestamp timestampVencimento = Timestamp.valueOf(dataVencimento.atStartOfDay());
	        	        
	        try {
				financeiroDAO.prepareToUpdate(financeiroVO)
				.set("DTVENC", timestampVencimento)
				.update();
			} catch (Exception e) {
				System.out.println("Erro data de corte - Metropolitana/Gustavo: "+e.getMessage());
				e.printStackTrace();
			}
	        
	    }
		
	}

	@Override
	public void afterUpdate(PersistenceEvent event) throws Exception {

		
	}

	@Override
	public void beforeCommit(TransactionContext arg0) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void beforeDelete(PersistenceEvent arg0) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void beforeInsert(PersistenceEvent event) throws Exception {
		
	}
	
	private LocalDate ajustarParaUltimoDiaDoMes(LocalDate data) {
	    // Se o dia original for maior que o último dia do mês, ajusta para o último dia
	    int diaOriginal = data.getDayOfMonth();
	    int ultimoDiaMes = data.lengthOfMonth();
	    
	    if (diaOriginal > ultimoDiaMes) {
	        return data.withDayOfMonth(ultimoDiaMes);
	    }
	    return data;
	}

	public boolean existefaturHH(BigDecimal nunota) throws Exception {
		JapeWrapper faturamentosHHDAO = JapeFactory.dao("AD_TABFATURAMENTO");
		if(nunota != null) {
			DynamicVO faturamentoHHVO = faturamentosHHDAO.findOne("NUNOTA = ?",nunota);
			
			if(faturamentoHHVO != null) {
				return true;
			}
		}
		return false;
		
	}

	@Override
	public void beforeUpdate(PersistenceEvent arg0) throws Exception {
		// TODO Auto-generated method stub
		
	}

}
